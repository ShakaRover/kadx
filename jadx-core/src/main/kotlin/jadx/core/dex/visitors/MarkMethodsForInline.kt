package jadx.core.dex.visitors

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.MethodInlineAttr
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.fixaccessmodifiers.FixAccessModifiers
import jadx.core.utils.BlockUtils
import jadx.core.utils.ListUtils
import jadx.core.utils.exceptions.JadxException

/**
 * 分析被 [ProcessMethodsForInline] 标记的方法，判断能否内联并生成内联替换指令。
 *
 * **做什么**：只处理“1 条 return/单指令”或“2 条指令且第 2 条是 return”的合成访问方法，
 * 识别字段 getter/setter、方法调用等模式，生成去除结果寄存器的内联指令，
 * 并修正被调用代码的可见性（内联后代码会搬进调用方，需保证能访问）。
 *
 * **为什么用静态 [process]**：[InlineMethods] 在处理调用点时会再次调用它（含强制加载类后重试）。
 */
@JadxVisitor(
	name = "MarkMethodsForInline",
	desc = "Mark synthetic static methods for inline",
	runAfter = [
		FixAccessModifiers::class,
		ClassModifier::class,
	],
)
class MarkMethodsForInline : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		process(mth)
	}

	companion object {
		/**
		 * @return 若方法尚未加载、无法分析则返回 null
		 */
		@JvmStatic
		fun process(mth: MethodNode): MethodInlineAttr? {
			try {
				val mia = mth.get(AType.METHOD_INLINE)
				if (mia != null) {
					return mia
				}
				if (mth.contains(AFlag.METHOD_CANDIDATE_FOR_INLINE)) {
					if (mth.getBasicBlocks() == null) {
						return null
					}
					val inlined = inlineMth(mth)
					if (inlined != null) {
						return inlined
					}
				}
			} catch (e: Exception) {
				mth.addWarnComment("Method inline analysis failed", e)
			}
			return MethodInlineAttr.inlineNotNeeded(mth)
		}

		private fun inlineMth(mth: MethodNode): MethodInlineAttr? {
			val insns = BlockUtils.collectInsnsWithLimit(checkNotNull(mth.getBasicBlocks()), 2)
			val insnsCount = insns.size
			if (insnsCount == 0) {
				return null
			}
			if (insnsCount == 1) {
				val insn = insns[0]
				if (insn.getType() == InsnType.RETURN && insn.getArgsCount() == 1) {
					// 合成的字段 getter：把 'return' 的参数作为内联指令
					val arg = insn.getArg(0)
					if (!arg.isInsnWrap) {
						return null
					}
					return addInlineAttr(mth, (arg as InsnWrapArg).unWrapWithCopy(), true)
				}
				// 方法调用
				return addInlineAttr(mth, insn, false)
			}
			if (insnsCount == 2 && insns[1].getType() == InsnType.RETURN) {
				val firstInsn = insns[0]
				val retInsn = insns[1]
				if (retInsn.getArgsCount() == 0 ||
					isSyntheticAccessPattern(mth, firstInsn, retInsn)
				) {
					return addInlineAttr(mth, firstInsn, false)
				}
			}
			// TODO: 内联字段算术。已禁用的测试：TestAnonymousClass3a 与 TestAnonymousClass5
			return null
		}

		private fun isSyntheticAccessPattern(mth: MethodNode, firstInsn: InsnNode, retInsn: InsnNode): Boolean {
			val mthRegs = mth.getArgRegs()
			return when (firstInsn.getType()) {
				InsnType.IGET ->
					mthRegs.size == 1 &&
						retInsn.getArg(0).isSameVar(firstInsn.getResult()) &&
						firstInsn.getArg(0).isSameVar(mthRegs[0])

				InsnType.SGET -> mthRegs.isEmpty() &&
					retInsn.getArg(0).isSameVar(firstInsn.getResult())

				InsnType.IPUT ->
					mthRegs.size == 2 &&
						retInsn.getArg(0).isSameVar(mthRegs[1]) &&
						firstInsn.getArg(0).isSameVar(mthRegs[1]) &&
						firstInsn.getArg(1).isSameVar(mthRegs[0])

				InsnType.SPUT ->
					mthRegs.size == 1 &&
						retInsn.getArg(0).isSameVar(mthRegs[0]) &&
						firstInsn.getArg(0).isSameVar(mthRegs[0])

				InsnType.INVOKE -> {
					if (!retInsn.getArg(0).isSameVar(firstInsn.getResult())) {
						return false
					}
					ListUtils.orderedEquals(mth.getArgRegs(), firstInsn.getArgList()) { mthArg, insnArg ->
						insnArg.isSameVar(mthArg)
					}
				}

				else -> false
			}
		}

		private fun addInlineAttr(mth: MethodNode, insn: InsnNode, isCopy: Boolean): MethodInlineAttr? {
			if (!fixVisibilityOfInlineCode(mth, insn)) {
				if (isCopy) {
					unbindSsaVars(insn)
				}
				return null
			}
			val inlInsn = if (isCopy) insn else insn.copyWithoutResult()
			unbindSsaVars(inlInsn)
			return MethodInlineAttr.markForInline(mth, inlInsn)
		}

		private fun unbindSsaVars(insn: InsnNode) {
			insn.visitArgs(
				{ arg ->
					if (arg.isRegister) {
						val reg = arg as RegisterArg
						val ssaVar = reg.sVar
						if (ssaVar != null) {
							ssaVar.removeUse(reg)
							reg.resetSSAVar()
						}
					}
				},
			)
		}

		private fun fixVisibilityOfInlineCode(mth: MethodNode, insn: InsnNode): Boolean {
			val newVisFlag = AccessFlags.PUBLIC // TODO: 更精确地计算
			val insnType = insn.getType()
			if (insnType == InsnType.INVOKE) {
				val invoke = insn as InvokeNode
				val callMthNode = mth.root().resolveMethod(invoke.callMth)
				if (callMthNode != null && !callMthNode.root().getArgs().isRespectBytecodeAccModifiers) {
					FixAccessModifiers.changeVisibility(callMthNode, newVisFlag)
				}
				return true
			}
			if (insnType == InsnType.ONE_ARG) {
				val arg = insn.getArg(0)
				if (!arg.isInsnWrap) {
					return false
				}
				return fixVisibilityOfInlineCode(mth, (arg as InsnWrapArg).wrapInsn)
			}
			if (insn is IndexInsnNode) {
				val indexObj = insn.index
				if (indexObj is FieldInfo) {
					// 字段访问已在 ModVisitor.fixFieldUsage 中修正
					return true
				}
			}
			mth.addDebugComment("Can't inline method, not implemented redirect type for insn: $insn")
			return false
		}
	}
}
