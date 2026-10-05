package kadx.core.dex.visitors

import kadx.api.plugins.input.data.annotations.AnnotationVisibility
import kadx.api.plugins.input.data.annotations.EncodedType
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import kadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.AttrNode
import kadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.instructions.ArithNode
import kadx.core.dex.instructions.ConstClassNode
import kadx.core.dex.instructions.ConstStringNode
import kadx.core.dex.instructions.FillArrayInsn
import kadx.core.dex.instructions.FilledNewArrayNode
import kadx.core.dex.instructions.IfNode
import kadx.core.dex.instructions.IfOp
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.NewArrayNode
import kadx.core.dex.instructions.SwitchInsn
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.instructions.args.NamedArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.instructions.mods.TernaryInsn
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.IFieldInfoRef
import kadx.core.dex.nodes.IMethodDetails
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.conditions.IfCondition
import kadx.core.dex.trycatch.ExcHandlerAttr
import kadx.core.dex.trycatch.ExceptionHandler
import kadx.core.dex.visitors.regions.variables.ProcessVariables
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.dex.visitors.typeinference.TypeCompareEnum
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnRemover
import kadx.core.utils.InsnUtils
import kadx.core.utils.ListUtils
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Objects

/**
 * 修改方法指令（删除、替换、处理异常处理器）。
 *
 * **做什么**：
 * - 用常量字段替换字面量常量（`replaceConsts` 选项）；
 * - 匿名类构造器参数处理（禁止内联、变量 final）；
 * - 把 `new-array`+`fill-array` 合成填充数组；
 * - 修正字段访问可见性（必要时插入 cast）；
 * - 内联 `cmp` 到 `if`、修正布尔 cast、删除无用指令。
 *
 * **为什么单独成 Pass**：这些修改会破坏寄存器依赖，因此必须在代码收缩/变量处理之前完成。
 */
@KadxVisitor(
	name = "ModVisitor",
	desc = "Modify method instructions",
	runBefore = [
		CodeShrinkVisitor::class,
		ProcessVariables::class,
	],
)
class ModVisitor : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		replaceConstInAnnotations(cls)
		return true
	}

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		val remover = InsnRemover(mth)
		replaceStep(mth, remover)
		removeStep(mth, remover)
		iterativeRemoveStep(mth)
	}

	private fun replaceConstInAnnotations(cls: ClassNode) {
		if (cls.root().getArgs().isReplaceConsts) {
			replaceConstsInAnnotationForAttrNode(cls, cls)
			for (f in cls.fields) {
				replaceConstsInAnnotationForAttrNode(cls, f)
			}
			for (m in cls.methods) {
				replaceConstsInAnnotationForAttrNode(cls, m)
				replaceConstsInAnnotationForMethodParamsAttr(cls, m)
			}
		}
	}

	private fun replaceConstsInAnnotationForMethodParamsAttr(cls: ClassNode, m: MethodNode) {
		val paramsAnnotation = m.get(KadxAttrType.ANNOTATION_MTH_PARAMETERS) ?: return
		for (annotationsList in paramsAnnotation.paramList) {
			replaceConstsInAnnotationsAttr(cls, annotationsList)
		}
	}

	private fun replaceConstsInAnnotationForAttrNode(parentCls: ClassNode, attrNode: AttrNode) {
		val annotationsList = attrNode.get(KadxAttrType.ANNOTATION_LIST)
		replaceConstsInAnnotationsAttr(parentCls, annotationsList)
	}

	private fun replaceConstsInAnnotationsAttr(parentCls: ClassNode, annotationsList: AnnotationsAttr?) {
		if (annotationsList == null) {
			return
		}
		for (annotation in annotationsList.all) {
			if (annotation.visibility == AnnotationVisibility.SYSTEM) {
				continue
			}
			val values = annotation.values as MutableMap<String, EncodedValue>
			for (entry in values.entries) {
				entry.setValue(replaceConstValue(parentCls, entry.value))
			}
		}
	}

	private fun replaceConstValue(parentCls: ClassNode, encodedValue: EncodedValue): EncodedValue {
		if (encodedValue.type == EncodedType.ENCODED_ANNOTATION) {
			val annotation = encodedValue.value as IAnnotation
			val values = annotation.values as MutableMap<String, EncodedValue>
			for (entry in values.entries) {
				entry.setValue(replaceConstValue(parentCls, entry.value))
			}
			return encodedValue
		}
		if (encodedValue.type == EncodedType.ENCODED_ARRAY) {
			val listVal = encodedValue.value as MutableList<EncodedValue>
			if (listVal.isNotEmpty()) {
				for (i in listVal.indices) {
					listVal[i] = replaceConstValue(parentCls, listVal[i])
				}
			}
			return EncodedValue(EncodedType.ENCODED_ARRAY, listVal)
		}
		val constField = parentCls.getConstField(encodedValue.value ?: return encodedValue)
		if (constField != null) {
			return EncodedValue(EncodedType.ENCODED_FIELD, constField.getFieldInfo())
		}
		return encodedValue
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(ModVisitor::class.java)

		private val DOUBLE_TO_BITS = java.lang.Double.doubleToLongBits(1.0)
		private val FLOAT_TO_BITS = java.lang.Float.floatToIntBits(1.0f).toLong()

		private fun replaceStep(mth: MethodNode, remover: InsnRemover) {
			val parentClass = mth.parentClass
			for (block in checkNotNull(mth.basicBlocks)) {
				remover.setBlock(block)
				val insnsList = block.instructions
				val size = insnsList.size
				for (i in 0 until size) {
					val insn = insnsList[i]
					when (insn.type) {
						InsnType.CONSTRUCTOR -> processAnonymousConstructor(mth, insn as ConstructorInsn)

						InsnType.CONST, InsnType.CONST_STR, InsnType.CONST_CLASS -> replaceConst(mth, parentClass, block, i, insn)

						InsnType.SWITCH -> replaceConstKeys(mth, parentClass, insn as SwitchInsn)

						InsnType.NEW_ARRAY -> {
							// 若下一条是 'fill-array'，则替换为填充数组
							val newArrInsn = insn as NewArrayNode
							val nextInsn = getFirstUseSkipMove(insn.result)
							if (nextInsn != null && nextInsn.type == InsnType.FILL_ARRAY) {
								val fillArrInsn = nextInsn as FillArrayInsn
								if (checkArrSizes(mth, newArrInsn, fillArrInsn)) {
									val filledArr = makeFilledArrayInsn(mth, newArrInsn, fillArrInsn)
									BlockUtils.replaceInsn(mth, block, i, filledArr)
									remover.addAndUnbind(nextInsn)
								}
							}
						}

						InsnType.MOVE_EXCEPTION -> processMoveException(mth, block, insn, remover)

						InsnType.ARITH -> processArith(mth, parentClass, insn as ArithNode)

						InsnType.CMP_L, InsnType.CMP_G -> inlineCMPInsns(mth, block, i, insn, remover)

						InsnType.CHECK_CAST -> removeCheckCast(mth, block, i, insn as IndexInsnNode)

						InsnType.CAST -> fixPrimitiveCast(mth, block, i, insn)

						InsnType.IPUT, InsnType.IGET -> fixFieldUsage(mth, insn as IndexInsnNode)

						else -> {}
					}
				}
				remover.perform()
			}
		}

		/**
		 * 若字段在调用点不可见 => cast 到声明类。
		 */
		private fun fixFieldUsage(mth: MethodNode, insn: IndexInsnNode) {
			val instanceArg = insn.getArg(if (insn.type == InsnType.IGET) 0 else 1)
			if (instanceArg.contains(AFlag.SUPER)) {
				return
			}
			if (instanceArg.isInsnWrap && (instanceArg as InsnWrapArg).wrapInsn.type == InsnType.CAST) {
				return
			}
			val fieldInfo = insn.index as FieldInfo
			val clsType = fieldInfo.declClass.type
			val instanceType = instanceArg.getType()
			if (Objects.equals(clsType, instanceType)) {
				// 不需要 cast
				return
			}

			val fieldNode = mth.root().resolveField(fieldInfo)
			if (fieldNode == null) {
				// 未知字段
				val result = mth.root().typeCompare.compareTypes(instanceType, clsType)
				if (result.isEqual() || (result == TypeCompareEnum.NARROW_BY_GENERIC && !instanceType.isGenericType())) {
					return
				}
			} else if (isFieldVisibleInMethod(fieldNode, mth)) {
				return
			}
			// 插入 cast
			val castInsn = IndexInsnNode(InsnType.CAST, clsType, 1)
			castInsn.addArg(instanceArg.duplicate())
			castInsn.add(AFlag.SYNTHETIC)
			castInsn.add(AFlag.EXPLICIT_CAST)

			val castArg = InsnArg.wrapInsnIntoArg(castInsn)
			castArg.setType(clsType)
			insn.replaceArg(instanceArg, castArg)
			InsnRemover.unbindArgUsage(mth, instanceArg)
		}

		private fun isFieldVisibleInMethod(field: FieldNode, mth: MethodNode): Boolean {
			val accessFlags: AccessInfo = field.accessFlags
			if (accessFlags.isPublic()) {
				return true
			}
			val useCls = mth.parentClass
			val fieldCls = field.parentClass
			val sameScope = Objects.equals(useCls, fieldCls) && !mth.accessFlags.isStatic()
			if (sameScope) {
				return true
			}
			if (accessFlags.isPrivate()) {
				return false
			}
			// package-private 或 protected
			if (Objects.equals(useCls.classInfo.getPackage(), fieldCls.classInfo.getPackage())) {
				// 同包
				return true
			}
			if (accessFlags.isPackagePrivate()) {
				return false
			}
			// protected
			val result = mth.root().typeCompare.compareTypes(useCls, fieldCls)
			return result == TypeCompareEnum.NARROW // 使用类是否是字段类的子类
		}

		private fun replaceConstKeys(mth: MethodNode, parentClass: ClassNode, insn: SwitchInsn) {
			val keys = insn.getKeys()
			val len = keys.size
			for (k in 0 until len) {
				val f = parentClass.getConstField(keys[k])
				if (f != null) {
					insn.modifyKey(k, f)
					addFieldUsage(f, mth)
				}
			}
		}

		private fun fixPrimitiveCast(mth: MethodNode, block: BlockNode, i: Int, insn: InsnNode) {
			// 把 boolean 到 (byte/char/short/long/double/float) 的 cast 替换为三元运算
			val castArg = insn.getArg(0)
			if (castArg.getType() == ArgType.BOOLEAN) {
				val type = checkNotNull(insn.result).getType()
				if (type.isPrimitive()) {
					val ternary = makeBooleanConvertInsn(checkNotNull(insn.result), castArg, type)
					BlockUtils.replaceInsn(mth, block, i, ternary)
				}
			}
		}

		fun makeBooleanConvertInsn(result: RegisterArg, castArg: InsnArg, type: ArgType): TernaryInsn {
			val zero = LiteralArg.make(0L, type)
			var litVal = 1L
			if (type == ArgType.DOUBLE) {
				litVal = DOUBLE_TO_BITS
			} else if (type == ArgType.FLOAT) {
				litVal = FLOAT_TO_BITS
			}
			val one = LiteralArg.make(litVal, type)

			val ifNode = IfNode(IfOp.EQ, -1, castArg, LiteralArg.litTrue())
			val condition = IfCondition.fromIfNode(ifNode)
			return TernaryInsn(condition, result, one, zero)
		}

		/**
		 * 把 CMP 指令内联到 'if' 中，便于后续条件合并。
		 */
		private fun inlineCMPInsns(mth: MethodNode, block: BlockNode, i: Int, insn: InsnNode, remover: InsnRemover) {
			val resArg = checkNotNull(insn.result)
			val useList = checkNotNull(resArg.sVar).useList
			if (ListUtils.allMatch(useList) { use -> InsnUtils.isInsnType(use.getParentInsn(), InsnType.IF) }) {
				for (useArg in ArrayList(useList)) {
					val useInsn = useArg.getParentInsn()
					if (useInsn != null) {
						val wrapArg = InsnArg.wrapInsnIntoArg(insn.copyWithoutResult())
						if (!useInsn.replaceArg(useArg, wrapArg)) {
							mth.addWarnComment("Failed to inline CMP insn: $insn into $useInsn")
							return
						}
					}
				}
				remover.addAndUnbind(insn)
			}
		}

		private fun checkArrSizes(mth: MethodNode, newArrInsn: NewArrayNode, fillArrInsn: FillArrayInsn): Boolean {
			val dataSize = fillArrInsn.size
			val arrSizeArg = newArrInsn.getArg(0)
			val value = InsnUtils.getConstValueByArg(mth.root(), arrSizeArg)
			if (value is LiteralArg) {
				val literal = value.literal
				return dataSize == literal.toInt()
			}
			return false
		}

		private fun removeCheckCast(mth: MethodNode, block: BlockNode, i: Int, insn: IndexInsnNode) {
			val castArg = insn.getArg(0)
			if (castArg.isZeroLiteral()) {
				// 总是保留 'null' 的 cast
				insn.add(AFlag.EXPLICIT_CAST)
				return
			}
			val castType = insn.index as ArgType
			if (!ArgType.isCastNeeded(mth.root(), castArg.getType(), castType)) {
				val result = checkNotNull(insn.result)
				val resultImType = result.sVar?.immutableType
				if (resultImType != null && resultImType != castArg.getType()) {
					// 结果寄存器带有与本次推断冲突的不可变类型（上游在此抛异常导致整个方法
					// 反编译失败）：保留原 cast 并跳过本优化——cast 在输出中无害
					return
				}
				result.setType(castArg.getType())

				val move = InsnNode(InsnType.MOVE, 1)
				move.setResult(result)
				move.addArg(castArg)
				BlockUtils.replaceInsn(mth, block, i, move)
				return
			}
			val prevCast = isCastDuplicate(insn)
			if (prevCast != null) {
				// 把前一个 cast 替换为 move
				val move = InsnNode(InsnType.MOVE, 1)
				move.setResult(prevCast.result)
				move.addArg(prevCast.getArg(0))
				BlockUtils.replaceInsn(mth, block, prevCast, move)
			}
		}

		private fun isCastDuplicate(castInsn: IndexInsnNode): InsnNode? {
			val arg = castInsn.getArg(0)
			if (arg.isRegister) {
				val sVar = (arg as RegisterArg).sVar
				if (sVar != null && sVar.useCount == 1 && !sVar.isUsedInPhi()) {
					val assignInsn = sVar.assign.getParentInsn()
					if (assignInsn != null && assignInsn.type == InsnType.CHECK_CAST) {
						val assignCastType = (assignInsn as IndexInsnNode).index
						if (assignCastType == castInsn.index) {
							return assignInsn
						}
					}
				}
			}
			return null
		}

		/**
		 * 删除无用指令。
		 */
		private fun removeStep(mth: MethodNode, remover: InsnRemover) {
			for (block in checkNotNull(mth.basicBlocks)) {
				remover.setBlock(block)
				for (insn in block.instructions) {
					when (insn.type) {
						InsnType.NOP, InsnType.GOTO, InsnType.NEW_INSTANCE -> remover.addAndUnbind(insn)

						else -> {
							if (insn.contains(AFlag.REMOVE)) {
								remover.addAndUnbind(insn)
							}
						}
					}
				}
				remover.perform()
			}
		}

		private fun iterativeRemoveStep(mth: MethodNode) {
			var changed: Boolean
			do {
				changed = false
				for (block in checkNotNull(mth.basicBlocks)) {
					for (insn in block.instructions) {
						if (insn.type == InsnType.MOVE &&
							insn.isAttrStorageEmpty() &&
							isResultArgNotUsed(insn)
						) {
							InsnRemover.remove(mth, block, insn)
							changed = true
							break
						}
					}
				}
			} while (changed)
		}

		private fun isResultArgNotUsed(insn: InsnNode): Boolean {
			val result = insn.result
			if (result != null) {
				val ssaVar = checkNotNull(result.sVar)
				return ssaVar.useCount == 0
			}
			return false
		}

		private fun replaceConst(mth: MethodNode, parentClass: ClassNode, block: BlockNode, i: Int, insn: InsnNode) {
			val f: IFieldInfoRef?
			if (insn.type == InsnType.CONST_STR) {
				val s = (insn as ConstStringNode).string
				f = if (s != null) parentClass.getConstField(s) else null
			} else if (insn.type == InsnType.CONST_CLASS) {
				val t = (insn as ConstClassNode).clsType
				f = parentClass.getConstField(t)
			} else {
				f = parentClass.getConstFieldByLiteralArg(insn.getArg(0) as LiteralArg)
			}
			if (f != null) {
				val inode = IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0)
				inode.setResult(insn.result)
				BlockUtils.replaceInsn(mth, block, i, inode)
				addFieldUsage(f, mth)
			}
		}

		private fun processArith(mth: MethodNode, parentClass: ClassNode, arithNode: ArithNode) {
			if (arithNode.argsCount != 2) {
				throw KadxRuntimeException("Invalid args count in insn: $arithNode")
			}
			val litArg = arithNode.getArg(1)
			if (litArg.isLiteral) {
				val f = parentClass.getConstFieldByLiteralArg(litArg as LiteralArg)
				if (f != null) {
					val fGet = IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0)
					if (arithNode.replaceArg(litArg, InsnArg.wrapArg(fGet))) {
						addFieldUsage(f, mth)
					}
				}
			}
		}

		/**
		 * 匿名构造器调用参数：
		 * - 禁止内联到构造器调用；
		 * - 把变量变为 final（编译器隐式要求）。
		 */
		private fun processAnonymousConstructor(mth: MethodNode, co: ConstructorInsn) {
			val callMthDetails = mth.root().getMethodUtils().getMethodDetails(co)
			if (callMthDetails !is MethodNode) {
				return
			}
			if (!callMthDetails.contains(AFlag.ANONYMOUS_CONSTRUCTOR) || callMthDetails.contains(AFlag.NO_SKIP_ARGS)) {
				return
			}
			val attr = callMthDetails.get(AType.SKIP_MTH_ARGS)
			if (attr != null) {
				val argsCount = minOf(callMthDetails.methodInfo.argsCount, co.argsCount)
				for (i in 0 until argsCount) {
					if (attr.isSkip(i)) {
						anonymousCallArgMod(co.getArg(i))
					}
				}
			} else {
				// 缺少额外信息时对所有参数应用（最安全的做法）
				for (arg in co.getArguments()) {
					anonymousCallArgMod(arg)
				}
			}
		}

		private fun anonymousCallArgMod(arg: InsnArg) {
			arg.add(AFlag.DONT_INLINE)
			if (arg.isRegister) {
				val sVar = (arg as RegisterArg).sVar
				if (sVar != null && sVar.isCodeVarSet()) {
					sVar.codeVar.isFinal = true
				} else {
					// 未绑定 CodeVar 的参数（中断的重处理周期残留）：跳过 final 标记
					LOG.debug("Skip final flag, codeVar not set: arg={} sVar={}", arg, sVar)
				}
			}
		}

		/**
		 * 返回 arg 的首次使用指令。
		 * 若只使用一次，尝试沿 move 链继续向下。
		 */
		private fun getFirstUseSkipMove(arg: RegisterArg?): InsnNode? {
			val sVar = checkNotNull(arg).sVar ?: return null
			val useCount = sVar.useCount
			if (useCount == 0) {
				return null
			}
			val useArg = sVar.useList[0]
			val parentInsn = useArg.getParentInsn() ?: return null
			if (useCount == 1 && parentInsn.type == InsnType.MOVE) {
				return getFirstUseSkipMove(parentInsn.result)
			}
			return parentInsn
		}

		private fun makeFilledArrayInsn(mth: MethodNode, newArrayNode: NewArrayNode, insn: FillArrayInsn): InsnNode {
			val insnArrayType = newArrayNode.arrayType
			val insnElementType = insnArrayType.getArrayElement()
			var elType = insn.elementType
			if (!elType.isTypeKnown() &&
				insnElementType != null &&
				insnElementType.isPrimitive() &&
				elType.contains(checkNotNull(insnElementType.getPrimitiveType()))
			) {
				elType = insnElementType
			}
			if (elType != insnElementType && insnArrayType != ArgType.OBJECT) {
				mth.addWarn(
					"Incorrect type for fill-array insn " + InsnUtils.formatOffset(insn.getOffset()) +
						", element type: " + elType + ", insn element type: " + insnElementType,
				)
			}
			if (!elType.isTypeKnown()) {
				LOG.warn("Unknown array element type: {} in mth: {}", elType, mth)
				val newElType = if (insnElementType != null && insnElementType.isTypeKnown()) insnElementType else elType.selectFirst()
				if (newElType == null) {
					throw KadxRuntimeException("Null array element type")
				}
				elType = newElType
			}

			val list = insn.getLiteralArgs(elType)
			val filledArr = FilledNewArrayNode(elType, list.size)
			filledArr.setResult(checkNotNull(newArrayNode.result).duplicate())
			for (arg in list) {
				val f = mth.parentClass.getConstFieldByLiteralArg(arg)
				if (f != null) {
					val fGet = IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0)
					filledArr.addArg(InsnArg.wrapArg(fGet))
					addFieldUsage(f, mth)
				} else {
					filledArr.addArg(arg.duplicate())
				}
			}
			return filledArr
		}

		private fun processMoveException(mth: MethodNode, block: BlockNode, insn: InsnNode, remover: InsnRemover) {
			val excHandlerAttr: ExcHandlerAttr = block.get(AType.EXC_HANDLER) ?: return
			val excHandler = excHandlerAttr.handler

			// 结果参数同时用于本指令和异常处理器
			val resArg = checkNotNull(insn.result)
			val type = excHandler.argType
			val name = if (excHandler.isCatchAll()) "th" else "e"
			if (resArg.name == null) {
				resArg.name = name
			}
			val sVar = checkNotNull(resArg.sVar)
			if (sVar.useCount == 0) {
				excHandler.setArg(NamedArg(name, type))
				remover.addAndUnbind(insn)
			} else if (sVar.isUsedInPhi()) {
				// 异常变量被移到外部变量 => 替换为 'move' 指令
				val moveInsn = InsnNode(InsnType.MOVE, 1)
				moveInsn.setResult(insn.result)
				val namedArg = NamedArg(name, type)
				moveInsn.addArg(namedArg)
				excHandler.setArg(namedArg)
				BlockUtils.replaceInsn(mth, block, 0, moveInsn)
			}
			block.copyAttributeFrom(insn, AType.CODE_COMMENTS) // 保存注释
		}

		fun addFieldUsage(fieldData: IFieldInfoRef, mth: MethodNode) {
			if (fieldData is FieldNode) {
				fieldData.addUseIn(mth)
			}
		}
	}
}
