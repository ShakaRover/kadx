package kadx.core.dex.visitors.usage

import kadx.api.metadata.ICodeAnnotation
import kadx.api.usage.IUsageInfoData
import kadx.api.usage.IUsageInfoVisitor
import kadx.core.clsp.ClspClassSource
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.ICodeNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.HashMap

/**
 * 使用信息（usage info）收集结果。
 *
 * **做什么**：保存整棵 DEX 树里“谁引用了谁”的映射，包括类依赖、类/字段/方法被谁使用、
 * 方法调用了哪些方法、未解析的方法引用、方法是否自调用等；随后把这些信息写回各个节点
 * （[apply]），或按类增量写回（[applyForClass]），或导出给外部访问者（[visitUsageData]）。
 *
 * **为什么这样设计**：usage 分析结果既服务于反混淆（名字排序）、也服务于内联与桥接方法
 * 合并等后续 Pass，因此需要可缓存（见 IUsageInfoCache）并可增量重算。
 *
 * **Kotlin 转换说明**：`UseSet` 是包内辅助类；Java 的 `==` 引用比较改为 `===`；
 * 集合类型使用 Kotlin 侧 `List/Set/Map`，仅在精确覆写 Java 接口时才用其原生签名。
 */
class UsageInfo(private val root: RootNode) : IUsageInfoData {

	private val clsDeps = UseSet<ClassNode, ClassNode>()
	private val clsUsage = UseSet<ClassNode, ClassNode>()
	private val clsUseInMth = UseSet<ClassNode, MethodNode>()
	private val fieldUsage = UseSet<FieldNode, MethodNode>()

	// MethodNodeA -> 调用 MethodNodeA 的方法集合
	private val mthUsage = UseSet<MethodNode, MethodNode>()

	// MethodNodeA -> MethodNodeA 调用的方法集合
	private val mthUses = UseSet<MethodNode, MethodNode>()

	// MethodNodeA -> MethodNodeA 调用但无法解析的方法签名集合
	private val unresolvedMthUsage = UseSet<MethodNode, MethodInfo>()

	private val selfCalls = HashMap<MethodNode, Boolean>()

	override fun apply() {
		clsDeps.visit { cls, deps -> cls.dependencies = sortedList(deps) }
		clsUsage.visit { cls, deps -> cls.useInValue = sortedList(deps) }
		clsUseInMth.visit { cls, methods -> cls.useInMth = resolveMthList(sortedList(methods)) }
		fieldUsage.visit { field, methods -> field.setUseIn(resolveMthList(sortedList(methods))) }
		mthUsage.visit { mth, methods -> mth.setUseIn(resolveMthList(sortedList(methods))) }
		mthUses.visit { mth, methods -> mth.setUsed(resolveMthList(sortedList(methods))) }
		unresolvedMthUsage.visit { mth, unresolvedMethods -> mth.setUnresolvedUsed(sortedList(unresolvedMethods)) }
		for ((mth, selfCall) in selfCalls) {
			mth.setCallsSelf(selfCall)
		}
	}

	override fun applyForClass(cls: ClassNode) {
		cls.dependencies = sortedList(clsDeps.getOrDefault(cls, emptySet()))
		cls.useInValue = sortedList(clsUsage.getOrDefault(cls, emptySet()))
		cls.useInMth = resolveMthList(sortedList(clsUseInMth.getOrDefault(cls, emptySet())))
		for (fld in cls.fields) {
			fld.setUseIn(resolveMthList(sortedList(fieldUsage.getOrDefault(fld, emptySet()))))
		}
		for (mth in cls.methods) {
			mth.setUseIn(resolveMthList(sortedList(mthUsage.getOrDefault(mth, emptySet()))))
			mth.setUsed(resolveMthList(sortedList(mthUses.getOrDefault(mth, emptySet()))))
			mth.setUnresolvedUsed(sortedList(unresolvedMthUsage.getOrDefault(mth, emptySet())))
			mth.setCallsSelf(selfCalls.getOrDefault(mth, false))
		}
	}

	override fun visitUsageData(visitor: IUsageInfoVisitor) {
		clsDeps.visit { cls, deps -> visitor.visitClassDeps(cls, sortedList(deps)) }
		clsUsage.visit { cls, deps -> visitor.visitClassUsage(cls, sortedList(deps)) }
		clsUseInMth.visit { cls, methods -> visitor.visitClassUseInMethods(cls, resolveMthList(sortedList(methods))) }
		fieldUsage.visit { field, methods -> visitor.visitFieldsUsage(field, resolveMthList(sortedList(methods))) }
		mthUsage.visit { mth, methods -> visitor.visitMethodsUsage(mth, resolveMthList(sortedList(methods))) }
		mthUses.visit { mth, methods -> visitor.visitMethodsUses(mth, resolveMthList(sortedList(methods))) }
		unresolvedMthUsage.visit { mth, unresolvedMethods -> visitor.visitUnresolvedMethodsUsage(mth, sortedList(unresolvedMethods)) }
		for ((mth, selfCall) in selfCalls) {
			visitor.visitIsSelfCall(mth, selfCall)
		}
		visitor.visitComplete()
	}

	fun clsUse(cls: ClassNode, useType: ArgType?) {
		processType(useType) { depCls -> clsUse(cls, depCls) }
	}

	fun clsUse(mth: MethodNode, useType: ArgType?) {
		processType(useType) { depCls -> clsUse(mth, depCls) }
	}

	fun clsUse(node: ICodeNode, useType: ArgType?) {
		val consumer: (ClassNode) -> Unit = when (node.annType) {
			ICodeAnnotation.AnnType.CLASS -> { depCls -> clsUse(node as ClassNode, depCls) }

			ICodeAnnotation.AnnType.METHOD -> { depCls -> clsUse(node as MethodNode, depCls) }

			ICodeAnnotation.AnnType.FIELD -> {
				val fldCls = (node as FieldNode).parentClass
				{ depCls -> clsUse(fldCls, depCls) }
			}

			else -> throw KadxRuntimeException("Unexpected use type: " + node.annType)
		}
		processType(useType, consumer)
	}

	fun clsUse(mth: MethodNode, useCls: ClassNode) {
		val parentClass = mth.parentClass
		clsUse(parentClass, useCls)
		if (parentClass !== useCls) {
			// 排除“类在自己的方法里被使用”
			clsUseInMth.add(useCls, mth)
		}
	}

	fun clsUse(cls: ClassNode, depCls: ClassNode) {
		val topParentClass = cls.topParentClass
		clsDeps.add(topParentClass, depCls.topParentClass)

		clsUsage.add(depCls, cls)
		clsUsage.add(depCls, topParentClass)
	}

	/** 记录方法使用：在 [mth] 的代码里发现了对 [useMth] 的调用。 */
	fun methodUse(mth: MethodNode, useMth: MethodNode) {
		clsUse(mth, useMth.parentClass)
		mthUsage.add(useMth, mth) // useMth 被 mth 调用
		mthUses.add(mth, useMth) // mth 调用了 useMth
		if (mth === useMth) {
			selfCalls[mth] = true
		}
		// 隐式使用
		clsUse(mth, useMth.returnType)
		for (argType in useMth.methodInfo.argumentsTypes) {
			clsUse(mth, argType)
		}
	}

	/** 记录无法解析的方法引用。 */
	fun unresolvedMethodUse(mth: MethodNode, useMth: MethodInfo) {
		if (useMth.rawFullId == "java.lang.Object.<init>()V") {
			// 忽略默认 Object 构造器（每个构造器都会调用）
			return
		}
		unresolvedMthUsage.add(mth, useMth)
	}

	fun fieldUse(mth: MethodNode, useFld: FieldNode) {
		clsUse(mth, useFld.parentClass)
		fieldUsage.add(useFld, mth)
		// 隐式使用
		clsUse(mth, useFld.type)
	}

	fun fieldUse(node: ICodeNode, useFld: FieldInfo) {
		val fld = root.resolveField(useFld) ?: return
		when (node.annType) {
			ICodeAnnotation.AnnType.CLASS -> {
				// TODO: 支持“类里的字段”用法？现在用字段父类代表“类里用类”
				clsUse(node as ClassNode, fld.parentClass)
			}

			ICodeAnnotation.AnnType.METHOD -> fieldUse(node as MethodNode, fld)

			else -> {}
		}
	}

	/** 递归处理某个类型（数组/泛型/上界/通配符）中出现的所有类。 */
	private fun processType(type: ArgType?, consumer: (ClassNode) -> Unit) {
		if (type == null || type === ArgType.OBJECT) {
			return
		}
		if (type.isArray()) {
			processType(type.getArrayRootElement(), consumer)
			return
		}
		if (type.isObject()) {
			// TODO: 支持通过 API 注册自定义处理器
			val clsDetails = checkNotNull(root.getClsp()).getClsDetails(type)
			if (clsDetails != null && clsDetails.source == ClspClassSource.APACHE_HTTP_LEGACY_CLIENT) {
				root.gradleInfoStorage.isUseApacheHttpLegacy = true
			}
			val clsNode = root.resolveClass(type)
			if (clsNode != null) {
				consumer(clsNode)
			}
			val genericTypes = type.getGenericTypes()
			if (genericTypes != null && genericTypes.isNotEmpty()) {
				for (argType in genericTypes) {
					processType(argType, consumer)
				}
			}
			val extendTypes = type.getExtendTypes()
			if (extendTypes.isNotEmpty()) {
				for (extendType in extendTypes) {
					processType(extendType, consumer)
				}
			}
			val wildcardType = type.getWildcardType()
			if (wildcardType != null) {
				processType(wildcardType, consumer)
			}
			// TODO: 处理 outer 类型（见 TestOuterGeneric 测试）
		}
	}

	private fun <T : Comparable<T>> sortedList(nodes: Set<T>?): List<T> {
		if (nodes == null || nodes.isEmpty()) {
			return emptyList()
		}
		val list = ArrayList(nodes)
		list.sort()
		return list
	}

	private fun resolveMthList(mthNodeList: List<MethodNode>): List<MethodNode> = Utils.collectionMap(mthNodeList) { m ->
		root.resolveDirectMethod(m.parentClass.rawName, m.methodInfo.shortId)
	}
}
