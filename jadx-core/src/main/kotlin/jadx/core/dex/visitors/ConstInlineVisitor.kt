package jadx.core.dex.visitors

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.IFieldInfoRef
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.finaly.MarkFinallyVisitor
import jadx.core.dex.visitors.ssa.SSATransform
import jadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import jadx.core.utils.InsnRemover
import jadx.core.utils.exceptions.JadxException
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 常量内联访问者。
 *
 * **做什么**：把只被使用一次的常量（CONST/MOVE/CONST_STR/CONST_CLASS）直接内联到
 * 使用它的指令里，从而消除中间寄存器与多余的赋值。
 *
 * **为什么**：DEX 里常量几乎总要先 `const` 到寄存器再使用，内联后代码更接近源码，
 * 也为后续的类型推断/变量合并创造条件。
 *
 * **注意**：对 null 常量要谨慎——若所有使用点都不允许 null（例如作为 invoke 的接收者），
 * 则不能内联。
 */
@JadxVisitor(
	name = "Constants Inline",
	desc = "Inline constant registers into instructions",
	runAfter = [SSATransform::class, MarkFinallyVisitor::class],
	runBefore = [TypeInferenceVisitor::class],
)
class ConstInlineVisitor : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		process(mth)
	}

	companion object {

		fun process(mth: MethodNode) {
			val toRemove = ArrayList<InsnNode>()
			for (block in checkNotNull(mth.basicBlocks)) {
				toRemove.clear()
				for (insn in block.instructions) {
					checkInsn(mth, insn, toRemove)
				}
				InsnRemover.removeAllAndUnbind(mth, block, toRemove)
			}
		}

		private fun checkInsn(mth: MethodNode, insn: InsnNode, toRemove: MutableList<InsnNode>) {
			if (insn.contains(AFlag.DONT_INLINE) ||
				insn.contains(AFlag.DONT_GENERATE) ||
				insn.result == null
			) {
				return
			}
			val sVar = checkNotNull(checkNotNull(insn.result).sVar)
			var constArg: InsnArg
			var onSuccess: Runnable? = null
			when (insn.type) {
				InsnType.CONST, InsnType.MOVE -> {
					constArg = insn.getArg(0)
					if (!constArg.isLiteral) {
						return
					}
					if (constArg.isZeroLiteral() && forbidNullInlines(sVar)) {
						// 所有使用点都禁止内联
						return
					}
				}

				InsnType.CONST_STR -> {
					val s = checkNotNull((insn as ConstStringNode).string)
					val f = mth.parentClass.getConstField(s)
					if (f == null) {
						val copy = insn.copyWithoutResult<InsnNode>()
						constArg = InsnArg.wrapArg(copy)
					} else {
						val constGet = IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0)
						constArg = InsnArg.wrapArg(constGet)
						constArg.setType(ArgType.STRING)
						onSuccess = Runnable { ModVisitor.addFieldUsage(f, mth) }
					}
				}

				InsnType.CONST_CLASS -> {
					if (sVar.isUsedInPhi()) {
						return
					}
					constArg = InsnArg.wrapArg(insn.copyWithoutResult<InsnNode>())
					constArg.setType(ArgType.CLASS)
				}

				else -> return
			}

			// 全部检查通过，执行替换
			if (replaceConst(mth, insn, constArg)) {
				toRemove.add(insn)
				onSuccess?.run()
			}
		}

		/** 不要内联 null 对象引用。 */
		private fun forbidNullInlines(sVar: SSAVar): Boolean {
			val useList = sVar.useList
			if (useList.isEmpty()) {
				return false
			}
			var k = 0
			for (useArg in useList) {
				val insn = useArg.getParentInsn()
				if (insn != null && forbidNullArgInline(insn, useArg)) {
					k++
				}
			}
			return k == useList.size
		}

		private fun forbidNullArgInline(insn: InsnNode, useArg: RegisterArg): Boolean {
			if (insn.type == InsnType.MOVE) {
				// 结果是 null，继续链式检查
				return forbidNullInlines(checkNotNull(checkNotNull(insn.result).sVar))
			}
			if (!canUseNull(insn, useArg)) {
				useArg.add(AFlag.DONT_INLINE_CONST)
				return true
			}
			return false
		}

		private fun canUseNull(insn: InsnNode, useArg: RegisterArg): Boolean {
			when (insn.type) {
				InsnType.INVOKE -> return (insn as InvokeNode).getInstanceArg() !== useArg

				InsnType.ARRAY_LENGTH,
				InsnType.AGET,
				InsnType.APUT,
				InsnType.IGET,
				InsnType.SWITCH,
				InsnType.MONITOR_ENTER,
				InsnType.MONITOR_EXIT,
				InsnType.INSTANCE_OF,
				-> return insn.getArg(0) !== useArg

				InsnType.IPUT -> return insn.getArg(1) !== useArg

				else -> {}
			}
			return true
		}

		private fun replaceConst(mth: MethodNode, constInsn: InsnNode, constArg: InsnArg): Boolean {
			val ssaVar = checkNotNull(checkNotNull(constInsn.result).sVar)
			if (ssaVar.useCount == 0) {
				return true
			}
			val useList = ArrayList(ssaVar.useList)
			var replaceCount = 0
			for (arg in useList) {
				if (canInline(mth, arg) && replaceArg(mth, arg, constArg, constInsn)) {
					replaceCount++
				}
			}
			if (replaceCount == useList.size) {
				return true
			}
			// 若仅被“不生成”的指令使用，则隐藏本指令
			var allIgnore = true
			for (reg in ssaVar.useList) {
				if (!canIgnoreInsn(reg)) {
					allIgnore = false
					break
				}
			}
			if (allIgnore) {
				constInsn.add(AFlag.DONT_GENERATE)
			}
			return false
		}

		private fun canIgnoreInsn(reg: RegisterArg): Boolean {
			val parentInsn = reg.getParentInsn()
			if (parentInsn == null || parentInsn.type == InsnType.PHI) {
				return false
			}
			if (reg.isLinkedToOtherSsaVars()) {
				return false
			}
			return parentInsn.contains(AFlag.DONT_GENERATE)
		}

		private fun canInline(mth: MethodNode, arg: RegisterArg): Boolean {
			if (arg.contains(AFlag.DONT_INLINE_CONST) || arg.contains(AFlag.DONT_INLINE)) {
				return false
			}
			val parentInsn = arg.getParentInsn() ?: return false
			if (parentInsn.contains(AFlag.DONT_GENERATE)) {
				return false
			}
			if (arg.isLinkedToOtherSsaVars() && !checkNotNull(arg.sVar).isUsedInPhi()) {
				// 不要内联 finally 块中使用的变量
				return false
			}
			if (parentInsn.type == InsnType.CONSTRUCTOR) {
				// 若匿名类调用后续可能被内联，则不要内联到其中
				val ctrMth = mth.root().getMethodUtils().resolveMethod(parentInsn as ConstructorInsn)
				if (ctrMth != null &&
					(ctrMth.contains(AFlag.METHOD_CANDIDATE_FOR_INLINE) || ctrMth.contains(AFlag.ANONYMOUS_CONSTRUCTOR))
				) {
					return false
				}
			}
			return true
		}

		private fun replaceArg(mth: MethodNode, arg: RegisterArg, constArg: InsnArg, constInsn: InsnNode): Boolean {
			val useInsn = arg.getParentInsn() ?: return false
			val insnType = useInsn.type
			if (insnType == InsnType.PHI) {
				return false
			}

			if (constArg.isLiteral) {
				val literal = (constArg as LiteralArg).literal
				var argType = arg.getType()
				if (argType === ArgType.UNKNOWN) {
					argType = arg.getInitType()
				}
				if (argType.isObject() && literal != 0L) {
					argType = ArgType.NARROW_NUMBERS
				}
				val litArg = InsnArg.lit(literal, argType)
				litArg.copyAttributesFrom(constArg)
				if (!useInsn.replaceArg(arg, litArg)) {
					return false
				}
				// 参数已替换，做一些优化
				var fieldNode: IFieldInfoRef? = null
				val litArgType = litArg.getType()
				if (litArgType.isTypeKnown()) {
					fieldNode = mth.parentClass.getConstFieldByLiteralArg(litArg)
				} else if (litArgType.contains(PrimitiveType.INT)) {
					fieldNode = mth.parentClass.getConstField(literal.toInt(), false)
				}
				if (fieldNode != null) {
					val sgetInsn = IndexInsnNode(InsnType.SGET, fieldNode.getFieldInfo(), 0)
					if (litArg.wrapInstruction(mth, sgetInsn) != null) {
						ModVisitor.addFieldUsage(fieldNode, mth)
					}
				} else {
					if (useInsn is BaseInvokeNode && useInsn.getInstanceArg() === litArg && !litArg.isZeroLiteral()) {
						// 字面量落在 invoke 接收者位置（对象类型寄存器与 int 常量冲突的边缘状态）：
						// 撤销本次内联、保留寄存器赋值——上游在 addExplicitCast 中抛异常
						// 导致整个方法反编译失败
						useInsn.replaceArg(litArg, arg)
						return false
					}
					addExplicitCast(useInsn, litArg)
				}
			} else {
				if (!useInsn.replaceArg(arg, constArg.duplicate())) {
					return false
				}
			}
			useInsn.inheritMetadata(constInsn)
			return true
		}

		private fun addExplicitCast(insn: InsnNode, arg: LiteralArg) {
			if (insn is BaseInvokeNode) {
				val callMth: MethodInfo = insn.callMth
				if (insn.getInstanceArg() === arg) {
					// 实例参数为 null，强制加 cast；非零字面量走到这里说明上方撤销内联
					// 未生效（防御路径）：跳过 cast，不再抛异常
					if (!arg.isZeroLiteral()) {
						return
					}
					val castType = callMth.declClass.type
					val castInsn = IndexInsnNode(InsnType.CAST, castType, 1)
					castInsn.addArg(arg)
					castInsn.add(AFlag.EXPLICIT_CAST)
					val wrapCast = InsnArg.wrapArg(castInsn)
					wrapCast.setType(castType)
					insn.replaceArg(arg, wrapCast)
				} else {
					val offset = insn.getFirstArgOffset()
					val argIndex = insn.getArgIndex(arg)
					val argType = callMth.argumentsTypes[argIndex - offset]
					if (argType.isPrimitive()) {
						arg.setType(argType)
						if (argType == ArgType.BYTE) {
							arg.add(AFlag.EXPLICIT_PRIMITIVE_TYPE)
						}
					}
				}
			}
		}
	}
}
