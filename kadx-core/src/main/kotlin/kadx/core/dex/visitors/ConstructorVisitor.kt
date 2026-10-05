package kadx.core.dex.visitors

import kadx.core.codegen.TypeGen
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.PhiInsn
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.ssa.SSATransform
import kadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnRemover
import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * 构造器调用访问者。
 *
 * **做什么**：把 DEX 的 `invoke-direct <init>` 调用改写成 kadx 的 [ConstructorInsn]，
 * 并处理 `new-instance` + `<init>` 的组合、分支构造器结果的 PHI 合并、以及
 * “全 null 参数的合成构造器”重定向到默认构造器。
 *
 * **为什么**：DEX 把对象创建拆成两条指令（NEW_INSTANCE + INVOKE），
 * 还原成源码中的 `new Foo(...)` 需要先合并它们，并区分 super/this/普通构造器调用。
 */
@KadxVisitor(
	name = "ConstructorVisitor",
	desc = "Replace invoke with constructor call",
	runAfter = [SSATransform::class, MoveInlineVisitor::class],
	runBefore = [TypeInferenceVisitor::class],
)
class ConstructorVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		if (replaceInvoke(mth)) {
			MoveInlineVisitor.moveInline(mth)
		}
	}

	companion object {

		private fun replaceInvoke(mth: MethodNode): Boolean {
			var replaced = false
			val remover = InsnRemover(mth)
			for (block in checkNotNull(mth.basicBlocks)) {
				remover.setBlock(block)
				val size = block.instructions.size
				for (i in 0 until size) {
					val insn = block.instructions[i]
					if (insn.type == InsnType.INVOKE) {
						// 注意：不能用 || 短路，processInvoke 必须每次都执行
						if (processInvoke(mth, block, i, remover)) {
							replaced = true
						}
					}
				}
				remover.perform()
			}
			return replaced
		}

		private fun processInvoke(mth: MethodNode, block: BlockNode, indexInBlock: Int, remover: InsnRemover): Boolean {
			val inv = block.instructions[indexInBlock] as InvokeNode
			var callMth = inv.callMth
			if (!callMth.isConstructor()) {
				return false
			}
			val instType = searchInstanceType(inv)
			if (instType != null && instType != callMth.declClass.type) {
				val instCls = ClassInfo.fromType(mth.root(), instType)
				callMth = MethodInfo.fromDetails(mth.root(), instCls, callMth.name, callMth.argumentsTypes, callMth.returnType)
			}
			val co = ConstructorInsn(mth, inv, callMth)
			if (canRemoveConstructor(mth, co)) {
				remover.addAndUnbind(inv)
				return false
			}
			co.inheritMetadata(inv)

			var instanceArg = inv.getArg(0) as RegisterArg
			checkNotNull(instanceArg.sVar).removeUse(instanceArg)
			if (co.isNewInstance) {
				val assignInsn = instanceArg.assignInsn
				if (assignInsn != null) {
					if (assignInsn.type == InsnType.CONSTRUCTOR) {
						// 该参数已用于另一条构造器指令
						// 插入新的 PHI 指令合并分支构造器的结果
						instanceArg = insertPhiInsn(mth, block, instanceArg, assignInsn as ConstructorInsn)
					} else {
						val newInstInsn = removeAssignChain(mth, assignInsn, remover, InsnType.NEW_INSTANCE)
						if (newInstInsn != null) {
							co.inheritMetadata(newInstInsn)
							newInstInsn.add(AFlag.REMOVE)
							remover.addWithoutUnbind(newInstInsn)
						}
					}
				}
				// 把实例参数从 'use' 转换为 'assign'
				co.setResult(instanceArg.duplicate())
			}
			co.rebindArgs()
			val replace = processConstructor(mth, co)
			if (replace != null) {
				remover.addAndUnbind(co)
				BlockUtils.replaceInsn(mth, block, indexInBlock, replace)
			} else {
				BlockUtils.replaceInsn(mth, block, indexInBlock, co)
			}
			return true
		}

		private fun searchInstanceType(inv: InvokeNode): ArgType? {
			val instanceArg = inv.getInstanceArg()
			if (instanceArg == null || !instanceArg.isRegister) {
				return null
			}
			val assignInsn = checkNotNull((instanceArg as RegisterArg).sVar).assignInsn
			if (assignInsn == null || assignInsn.type != InsnType.NEW_INSTANCE) {
				return null
			}
			return (assignInsn as IndexInsnNode).indexAsType
		}

		private fun insertPhiInsn(
			mth: MethodNode,
			curBlock: BlockNode,
			instArg: RegisterArg,
			otherCtr: ConstructorInsn,
		): RegisterArg {
			val otherBlock = BlockUtils.getBlockByInsn(mth, otherCtr)
				?: throw KadxRuntimeException("Block not found by insn: $otherCtr")
			val crossBlock = BlockUtils.getPathCross(mth, curBlock, otherBlock)
			if (crossBlock == null) {
				// 无路径交叉 -> 不需要 PHI 指令
				// 在当前路径的使用点上换用新的 SSA 变量
				val newResArg = instArg.duplicateWithNewSSAVar(mth)
				val pathBlocks = BlockUtils.collectAllSuccessors(mth, curBlock, true)
				for (useReg in checkNotNull(instArg.sVar).useList) {
					val parentInsn = useReg.getParentInsn()
					if (parentInsn != null) {
						val useBlock = BlockUtils.getBlockByInsn(mth, parentInsn, pathBlocks)
						if (useBlock != null) {
							parentInsn.replaceArg(useReg, newResArg.duplicate())
						}
					}
				}
				return newResArg
			}
			val newResArg = instArg.duplicateWithNewSSAVar(mth)
			val useArg = checkNotNull(otherCtr.result)
			val otherResArg = useArg.duplicateWithNewSSAVar(mth)

			val phiInsn = SSATransform.addPhi(mth, crossBlock, useArg.regNum)
			phiInsn.setResult(useArg.duplicate())
			phiInsn.bindArg(newResArg.duplicate(), BlockUtils.getPrevBlockOnPath(mth, crossBlock, curBlock))
			phiInsn.bindArg(otherResArg.duplicate(), BlockUtils.getPrevBlockOnPath(mth, crossBlock, otherBlock))
			phiInsn.rebindArgs()

			otherCtr.setResult(otherResArg.duplicate())
			otherCtr.rebindArgs()
			return newResArg
		}

		private fun canRemoveConstructor(mth: MethodNode, co: ConstructorInsn): Boolean {
			val parentClass = mth.parentClass
			if (co.isSuper && co.argsCount == 0) {
				return true
			}
			if (co.isThis && co.argsCount == 0) {
				val defCo = parentClass.searchMethodByShortId(co.callMth.shortId)
				if (defCo == null || defCo.isNoCode()) {
					// 默认构造器未实现
					return true
				}
			}
			// 匿名类实例初始化器里的 super() 调用直接移除
			return parentClass.isAnonymous() && mth.isDefaultConstructor() && co.isSuper
		}

		/**
		 * 把“全 null 参数的合成构造器调用”替换为可能的非合成/默认构造器。
		 *
		 * @return 用于替换的指令；若无需或无法替换则返回 null
		 */
		private fun processConstructor(mth: MethodNode, co: ConstructorInsn): ConstructorInsn? {
			val callMth = mth.root().resolveMethod(co.callMth)
			if (callMth == null ||
				!callMth.accessFlags.isSynthetic() ||
				!allArgsNull(co)
			) {
				return null
			}
			val classNode = mth.root().resolveClass(callMth.parentClass.classInfo) ?: return null
			val instanceArg = co.result ?: return null
			val passThis = instanceArg.isThis()
			val ctrId = "<init>(" + (if (passThis) TypeGen.signature(instanceArg.getInitType()) else "") + ")V"
			val defCtr = classNode.searchMethodByShortId(ctrId)
			if (defCtr == null || defCtr == callMth || defCtr.accessFlags.isSynthetic()) {
				return null
			}
			val newInsn = ConstructorInsn(defCtr.methodInfo, co.callType)
			newInsn.setResult(checkNotNull(co.result).duplicate())
			newInsn.inheritMetadata(co)
			return newInsn
		}

		private fun allArgsNull(insn: ConstructorInsn): Boolean {
			for (insnArg in insn.getArguments()) {
				if (insnArg.isLiteral) {
					val lit = insnArg as LiteralArg
					if (lit.literal != 0L) {
						return false
					}
				} else {
					return false
				}
			}
			return true
		}

		/**
		 * 沿 'move' 链移除指令，直到遇到类型为 [insnType] 的指令。
		 */
		private fun removeAssignChain(mth: MethodNode, insn: InsnNode?, remover: InsnRemover, insnType: InsnType): InsnNode? {
			if (insn == null) {
				return null
			}
			val type = insn.type
			if (type == insnType) {
				return insn
			}
			if (insn.isAttrStorageEmpty()) {
				remover.addWithoutUnbind(insn)
			} else {
				BlockUtils.replaceInsn(mth, insn, InsnNode(InsnType.NOP, 0))
			}
			if (type == InsnType.MOVE) {
				val arg = insn.getArg(0) as RegisterArg
				return removeAssignChain(mth, arg.assignInsn, remover, insnType)
			}
			return null
		}
	}
}
