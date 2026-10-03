package jadx.core.dex.visitors.shrink

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeCustomNode
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.Named
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.ModVisitor
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnList
import jadx.core.utils.InsnRemover
import jadx.core.utils.RegionUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.BitSet
import java.util.HashSet
import java.util.Objects

/**
 * 代码收缩 Pass：把只用一次的临时变量内联到使用处，让生成代码更紧凑。
 *
 * **做什么**：逐基本块分析指令之间的依赖（[ArgsInfo]），把“赋值给临时变量、
 * 且该变量只被使用一次”的指令搬进使用它的指令里（wrap），并简化 `move` 指令。
 *
 * **为什么需要**：DEX/字节码由编译器生成时会产生大量临时寄存器，
 * 直接反编译会得到一堆 `v0 = ...; use(v0)`，内联后才是可读的表达式。
 *
 * **Kotlin 转换说明**：`shrinkMethod` 是公共静态入口，放 companion + `@JvmStatic`；
 * 原 Java `==` 引用比较改为 `===`；集合用 Kotlin 侧类型，性能敏感处保持普通循环。
 */
@JadxVisitor(
	name = "CodeShrinkVisitor",
	desc = "Inline variables to make code smaller",
	runAfter = [ModVisitor::class],
)
class CodeShrinkVisitor : AbstractVisitor() {

	@Throws(jadx.core.utils.exceptions.JadxException::class)
	override fun visit(mth: MethodNode) {
		shrinkMethod(mth)
	}

	companion object {
		/** 对单个方法执行收缩（供其它 Pass 在修改代码后调用）。 */
		fun shrinkMethod(mth: MethodNode) {
			if (mth.isNoCode()) {
				return
			}
			mth.remove(AFlag.REQUEST_CODE_SHRINK)
			for (block in checkNotNull(mth.basicBlocks)) {
				shrinkBlock(mth, block)
				simplifyMoveInsns(mth, block)
			}
		}

		private fun shrinkBlock(mth: MethodNode, block: BlockNode) {
			if (block.instructions.isEmpty()) {
				return
			}
			val insnList = InsnList(block.instructions)
			val insnCount = insnList.size()
			val argsList = ArrayList<ArgsInfo>(insnCount)
			for (i in 0 until insnCount) {
				argsList.add(ArgsInfo(insnList.get(i), argsList, i))
			}
			val wrapList = ArrayList<WrapInfo>()
			for (argsInfo in argsList) {
				val args = argsInfo.args
				for (i in args.size - 1 downTo 0) {
					val arg = args[i]
					checkInline(mth, block, insnList, wrapList, argsInfo, arg)
				}
			}
			if (wrapList.isNotEmpty()) {
				for (wrapInfo in wrapList) {
					inline(mth, wrapInfo.arg, wrapInfo.insn, block)
				}
			}
		}

		private fun checkInline(
			mth: MethodNode,
			block: BlockNode,
			insnList: InsnList,
			wrapList: MutableList<WrapInfo>,
			argsInfo: ArgsInfo,
			arg: RegisterArg,
		) {
			val parentInsn = arg.getParentInsn()
			if (arg.contains(AFlag.DONT_INLINE) ||
				parentInsn == null ||
				parentInsn.contains(AFlag.DONT_GENERATE)
			) {
				return
			}
			val sVar = arg.sVar
			if (sVar == null || sVar.assign.contains(AFlag.DONT_INLINE)) {
				return
			}
			val assignInsn = sVar.assign.getParentInsn()
			if (assignInsn == null ||
				assignInsn.contains(AFlag.DONT_INLINE) ||
				assignInsn.contains(AFlag.WRAPPED)
			) {
				return
			}
			val assignInline = assignInsn.contains(AFlag.FORCE_ASSIGN_INLINE)
			if (!assignInline && sVar.isUsedInPhi()) {
				return
			}
			// 只允许内联“仅使用一次”的参数
			var useCount = 0
			for (useArg in sVar.useList) {
				val useParentInsn = useArg.getParentInsn()
				if (useParentInsn != null && useParentInsn.contains(AFlag.DONT_GENERATE)) {
					continue
				}
				if (!assignInline && useArg.contains(AFlag.DONT_INLINE_CONST)) {
					return
				}
				useCount++
			}
			if (!assignInline && useCount != 1) {
				return
			}
			val sVarName = sVar.getName()
			if (!assignInline && sVarName != null) {
				if (searchArgWithName(assignInsn, sVarName)) {
					// 名字在结果里被复用，允许内联
				} else if (varWithSameNameExists(mth, sVar)) {
					// 变量名重复，允许内联
				} else {
					// 有名字的变量拒绝内联
					return
				}
			}
			if (!checkLambdaInline(arg, assignInsn)) {
				return
			}

			val assignPos = insnList.getIndex(assignInsn)
			if (assignPos != -1) {
				val wrapInfo = argsInfo.checkInline(assignPos, arg)
				if (wrapInfo != null) {
					wrapList.add(wrapInfo)
				}
			} else {
				// 在另一个基本块
				val assignBlock = BlockUtils.getBlockByInsn(mth, assignInsn)
				if (assignBlock != null &&
					assignInsn !== arg.getParentInsn() &&
					canMoveBetweenBlocks(mth, assignInsn, assignBlock, block, argsInfo.insn)
				) {
					if (assignInline) {
						assignInline(mth, arg, assignInsn, assignBlock)
					} else {
						inline(mth, arg, assignInsn, assignBlock)
					}
				}
			}
		}

		/**
		 * 禁止把 lambda 内联进 invoke 的实例参数位置，否则生成代码无法编译：
		 * {@code () -> { ... }.apply(); }
		 */
		private fun checkLambdaInline(arg: RegisterArg, assignInsn: InsnNode): Boolean {
			if (assignInsn.type == InsnType.INVOKE && assignInsn is InvokeCustomNode) {
				for (useArg in checkNotNull(arg.sVar).useList) {
					val parentInsn = useArg.getParentInsn()
					if (parentInsn != null && parentInsn.type == InsnType.INVOKE) {
						val invokeNode = parentInsn as InvokeNode
						val instArg = invokeNode.getInstanceArg()
						if (instArg != null && instArg === useArg) {
							return false
						}
					}
				}
			}
			return true
		}

		private fun varWithSameNameExists(mth: MethodNode, inlineVar: SSAVar): Boolean {
			for (ssaVar in mth.SVars) {
				if (ssaVar === inlineVar || ssaVar.codeVar === inlineVar.codeVar) {
					continue
				}
				if (Objects.equals(ssaVar.getName(), inlineVar.getName())) {
					return ssaVar.useCount > inlineVar.useCount
				}
			}
			return false
		}

		private fun searchArgWithName(assignInsn: InsnNode, varName: String): Boolean {
			val result = assignInsn.visitArgs<InsnArg> { insnArg ->
				if (insnArg is Named) {
					if (insnArg.name == varName) {
						return@visitArgs insnArg
					}
				}
				null
			}
			return result != null
		}

		private fun assignInline(mth: MethodNode, arg: RegisterArg, assignInsn: InsnNode, assignBlock: BlockNode): Boolean {
			val useArg = checkNotNull(arg.sVar).useList[0]
			val useInsn = useArg.getParentInsn()
			if (useInsn == null || useInsn.contains(AFlag.DONT_GENERATE)) {
				return false
			}
			if (!InsnRemover.removeWithoutUnbind(mth, assignBlock, assignInsn)) {
				return false
			}
			useArg.wrapInstruction(mth, assignInsn)
			return true
		}

		private fun inline(mth: MethodNode, arg: RegisterArg, insn: InsnNode, block: BlockNode): Boolean {
			if (insn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
				return assignInline(mth, arg, insn, block)
			}
			// 只把指令搬进参数，不做 unbind/copy/duplicate
			val wrappedArg = arg.wrapInstruction(mth, insn, false)
			val replaced = wrappedArg != null
			if (replaced) {
				val parentInsn = arg.getParentInsn()
				if (parentInsn != null) {
					parentInsn.inheritMetadata(insn)
				}
				InsnRemover.unbindResult(mth, insn)
				InsnRemover.removeWithoutUnbind(mth, block, insn)
			}
			return replaced
		}

		private fun canMoveBetweenBlocks(
			mth: MethodNode,
			assignInsn: InsnNode,
			assignBlock: BlockNode,
			useBlock: BlockNode,
			useInsn: InsnNode,
		): Boolean {
			if (!BlockUtils.isPathExists(assignBlock, useBlock)) {
				return false
			}

			val argsList = ArgsInfo.getArgs(assignInsn)
			val args = BitSet()
			for (arg in argsList) {
				args.set(arg.regNum)
			}
			var startCheck = false
			for (insn in assignBlock.getInstructions()) {
				if (startCheck && (!insn.canReorder() || ArgsInfo.usedArgAssign(insn, args))) {
					return false
				}
				if (insn === assignInsn) {
					startCheck = true
				}
			}
			val pathsBlocks = HashSet(BlockUtils.getAllPathsBlocks(assignBlock, useBlock))
			pathsBlocks.remove(assignBlock)
			pathsBlocks.remove(useBlock)
			for (block in pathsBlocks) {
				if (block.contains(AFlag.DONT_GENERATE)) {
					if (BlockUtils.checkLastInsnType(block, InsnType.MONITOR_EXIT)) {
						if (RegionUtils.isBlocksInSameRegion(mth, assignBlock, useBlock)) {
							// 允许在同一 synchronized 区域内移动
						} else {
							// 不从 synchronized 块中移出
							return false
						}
					}
					// 跳过不生成代码的块
					continue
				}
				for (insn in block.getInstructions()) {
					if (!insn.canReorder() || ArgsInfo.usedArgAssign(insn, args)) {
						return false
					}
				}
			}
			for (insn in useBlock.getInstructions()) {
				if (insn === useInsn) {
					return true
				}
				if (!insn.canReorder() || ArgsInfo.usedArgAssign(insn, args)) {
					return false
				}
			}
			throw JadxRuntimeException("Can't process instruction move : $assignBlock")
		}

		private fun simplifyMoveInsns(mth: MethodNode, block: BlockNode) {
			val insns = block.getInstructions()
			val size = insns.size
			for (i in 0 until size) {
				val insn = insns[i]
				if (insn.type == InsnType.MOVE) {
					// 用 wrapped 指令替换 move
					val arg = insn.getArg(0)
					if (arg.isInsnWrap) {
						val wrapInsn = (arg as InsnWrapArg).wrapInsn
						InsnRemover.unbindResult(mth, wrapInsn)
						wrapInsn.setResult(checkNotNull(insn.getResult()).duplicate())
						wrapInsn.inheritMetadata(insn)
						wrapInsn.setOffset(insn.getOffset())
						wrapInsn.remove(AFlag.WRAPPED)
						block.instructions[i] = wrapInsn
					}
				}
			}
		}
	}
}
