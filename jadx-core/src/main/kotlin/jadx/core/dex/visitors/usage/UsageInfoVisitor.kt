package jadx.core.dex.visitors.usage

import jadx.api.plugins.input.data.ICallSite
import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IFieldRef
import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import jadx.api.plugins.input.insns.InsnData
import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
import jadx.api.plugins.input.insns.custom.ICustomPayload
import jadx.api.usage.IUsageInfoCache
import jadx.api.usage.IUsageInfoData
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.ICodeNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.OverrideMethodVisitor
import jadx.core.dex.visitors.SignatureProcessor
import jadx.core.dex.visitors.rename.RenameVisitor
import jadx.core.utils.ListUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.input.InsnDataUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 用法信息收集 Pass。
 *
 * **做什么**：扫描所有类与方法的指令、注解，构建 [UsageInfo]（类依赖、方法/字段引用、
 * 覆写关系等），并在收集完成后把结果写回 AST 节点。若配置了缓存（[IUsageInfoCache]），
 * 则优先复用缓存结果，避免重复扫描。
 *
 * **为什么放在这里**：后续的重命名、内联、桥接方法合并、代码生成都需要知道
 * “谁引用了谁”，因此本 Pass 在 [SignatureProcessor] / [OverrideMethodVisitor] /
 * [RenameVisitor] 之后运行，以保证类型/覆写/别名信息已就绪。
 *
 * **Kotlin 转换说明**：`visitInstructions` 接收 Java `Consumer`，用 lambda 保持完整遍历；
 * 流式 `forEach` 改为普通 `for` 循环；`==` 引用比较改为 `===`。
 */
@JadxVisitor(
	name = "UsageInfoVisitor",
	desc = "Scan class and methods to collect usage info and class dependencies",
	runAfter = [
		SignatureProcessor::class, // 使用带泛型的类型
		OverrideMethodVisitor::class, // 把方法覆写计为使用
		RenameVisitor::class, // 按别名排序
	],
)
class UsageInfoVisitor : AbstractVisitor() {

	override fun init(root: RootNode) {
		val usageCache = root.getArgs().usageInfoCache
		val usageInfoData = usageCache.get(root)
		if (usageInfoData != null) {
			try {
				apply(usageInfoData)
				return
			} catch (e: Exception) {
				LOG.error("Failed to apply cached usage data", e)
			}
		}
		val collectedInfoData = buildUsageData(root)
		usageCache.set(root, collectedInfoData)
		apply(collectedInfoData)
	}

	override fun getName(): String = "UsageInfoVisitor"

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(UsageInfoVisitor::class.java)

		private fun apply(usageInfoData: IUsageInfoData) {
			val start = System.currentTimeMillis()
			usageInfoData.apply()
			if (LOG.isDebugEnabled) {
				LOG.debug("Apply usage data in {}ms", System.currentTimeMillis() - start)
			}
		}

		private fun buildUsageData(root: RootNode): IUsageInfoData {
			val usageInfo = UsageInfo(root)
			for (cls in root.getClasses()) {
				processClass(cls, usageInfo)
			}
			return usageInfo
		}

		private fun processClass(cls: ClassNode, usageInfo: UsageInfo) {
			usageInfo.clsUse(cls, cls.superClass)
			for (interfaceType in cls.interfaces) {
				usageInfo.clsUse(cls, interfaceType)
			}
			for (genericTypeParameter in cls.genericTypeParameters) {
				usageInfo.clsUse(cls, genericTypeParameter)
			}
			for (fieldNode in cls.fields) {
				usageInfo.clsUse(cls, fieldNode.type)
				processAnnotations(fieldNode, usageInfo)
				// TODO: 处理字段 'constant value' 中的类型
			}
			processAnnotations(cls, usageInfo)
			for (methodNode in cls.methods) {
				processMethod(methodNode, usageInfo)
			}
		}

		private fun processMethod(mth: MethodNode, usageInfo: UsageInfo) {
			processMethodAnnotations(mth, usageInfo)
			usageInfo.clsUse(mth, mth.returnType)
			for (argType in mth.argTypes) {
				usageInfo.clsUse(mth, argType)
			}
			// TODO: 处理 'throws' 中的异常类
			try {
				processInstructions(mth, usageInfo)
			} catch (e: Exception) {
				mth.addError("Dependency scan failed", e)
			}
		}

		private fun processInstructions(mth: MethodNode, usageInfo: UsageInfo) {
			if (mth.isNoCode()) {
				return
			}
			val codeReader: ICodeReader = mth.codeReader ?: return
			val root = mth.root()
			codeReader.visitInstructions { insnData ->
				try {
					processInsn(root, mth, insnData, usageInfo)
				} catch (e: Exception) {
					throw JadxRuntimeException(
						"Usage info collection failed with error: " + e.message + " at insn: " + insnData,
						e,
					)
				}
			}
		}

		private fun processInsn(root: RootNode, mth: MethodNode, insnData: InsnData, usageInfo: UsageInfo) {
			if (insnData.opcode == Opcode.UNKNOWN) {
				return
			}
			when (insnData.indexType) {
				InsnIndexType.TYPE_REF -> {
					insnData.decode()
					val usedType = ArgType.parse(insnData.indexAsType)
					usageInfo.clsUse(mth, usedType)
				}

				InsnIndexType.FIELD_REF -> {
					insnData.decode()
					val fieldNode = root.resolveField(FieldInfo.fromRef(root, checkNotNull(insnData.indexAsField)))
					if (fieldNode != null) {
						usageInfo.fieldUse(mth, fieldNode)
					}
				}

				InsnIndexType.METHOD_REF -> {
					insnData.decode()
					val payload = insnData.payload
					val mthRef: IMethodRef = if (payload != null) {
						payload as IMethodRef
					} else {
						checkNotNull(insnData.indexAsMethod)
					}
					val mthInfo = MethodInfo.fromRef(root, mthRef)
					val methodNode = root.resolveMethod(mthInfo)
					if (methodNode != null) {
						usageInfo.methodUse(mth, methodNode)
					} else {
						usageInfo.unresolvedMethodUse(mth, mthInfo)
					}
				}

				InsnIndexType.CALL_SITE -> {
					insnData.decode()
					val callSite: ICallSite? = InsnDataUtils.getCallSite(insnData)
					val methodHandle: IMethodHandle? = InsnDataUtils.getMethodHandleAt(callSite, 4)
					if (methodHandle != null) {
						val mthRef = methodHandle.methodRef
						if (mthRef != null) {
							val mthInfo = MethodInfo.fromRef(root, mthRef)
							val mthNode = root.resolveMethod(mthInfo)
							if (mthNode != null) {
								usageInfo.methodUse(mth, mthNode)
							} else {
								usageInfo.unresolvedMethodUse(mth, mthInfo)
							}
						}
					}
				}

				else -> {}
			}
		}

		private fun processAnnotations(node: ICodeNode, usageInfo: UsageInfo) {
			val annAttr = node.get(JadxAttrType.ANNOTATION_LIST)
			processAnnotationAttr(node, annAttr, usageInfo)
		}

		private fun processMethodAnnotations(mth: MethodNode, usageInfo: UsageInfo) {
			processAnnotations(mth, usageInfo)
			val paramsAttr = mth.get(JadxAttrType.ANNOTATION_MTH_PARAMETERS)
			if (paramsAttr != null) {
				for (annAttr in paramsAttr.paramList) {
					processAnnotationAttr(mth, annAttr, usageInfo)
				}
			}
		}

		private fun processAnnotationAttr(node: ICodeNode, annAttr: AnnotationsAttr?, usageInfo: UsageInfo) {
			if (annAttr == null || annAttr.isEmpty) {
				return
			}
			for (ann in annAttr.list) {
				processAnnotation(node, ann, usageInfo)
			}
		}

		private fun processAnnotation(node: ICodeNode, ann: IAnnotation, usageInfo: UsageInfo) {
			usageInfo.clsUse(node, ArgType.parse(ann.annotationClass))
			for (value in ann.values.values) {
				processAnnotationValue(node, value, usageInfo)
			}
		}

		@Suppress("UNCHECKED_CAST")
		private fun processAnnotationValue(node: ICodeNode, value: EncodedValue, usageInfo: UsageInfo) {
			val obj = value.value
			when (value.type) {
				EncodedType.ENCODED_TYPE -> usageInfo.clsUse(node, ArgType.parse(obj as String?))

				EncodedType.ENCODED_ENUM, EncodedType.ENCODED_FIELD -> {
					if (obj is IFieldRef) {
						usageInfo.fieldUse(node, FieldInfo.fromRef(node.root(), obj))
					} else if (obj is FieldInfo) {
						usageInfo.fieldUse(node, obj)
					} else {
						throw JadxRuntimeException("Unexpected field type class: " + value.javaClass)
					}
				}

				EncodedType.ENCODED_ARRAY -> {
					for (encodedValue in obj as List<EncodedValue>) {
						processAnnotationValue(node, encodedValue, usageInfo)
					}
				}

				EncodedType.ENCODED_ANNOTATION -> processAnnotation(node, obj as IAnnotation, usageInfo)

				else -> {}
			}
		}

		fun replaceMethodUsage(mergeIntoMth: MethodNode, sourceMth: MethodNode) {
			val mergedUsage = ArrayList(ListUtils.distinctMergeSortedLists(mergeIntoMth.useIn, sourceMth.useIn))
			mergedUsage.remove(sourceMth)
			mergeIntoMth.setUseIn(mergedUsage)
			sourceMth.setUseIn(emptyList())
		}
	}
}
