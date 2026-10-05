@file:Suppress("UNCHECKED_CAST")

package kadx.core.dex.visitors.blocks

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.JumpInfo
import kadx.core.dex.attributes.nodes.TmpEdgeAttr
import kadx.core.dex.instructions.IfNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.TargetInsnNode
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.trycatch.CatchAttr
import kadx.core.dex.trycatch.ExceptionHandler
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.EnumSet
import java.util.HashMap
import java.util.HashSet

/**
 * 基本块切分（CFG 构建的第一步）。
 *
 * **做什么**：把方法的一维指令序列按“分支/异常边界”切成基本块，并根据跳转指令建立块间边。
 *
 * **切分依据**（[splitBasicBlocks]）：遇到 return/throw/goto/if/switch/jsr/ret、
 * try 进入/离开、异常处理器边界等，就新开一个块。
 *
 * **后续步骤**：
 * - [setupConnectionsFromJumps] 根据 `JumpInfo` 属性连边；
 * - [addTempConnectionsForExcHandlers] 为异常处理器临时连边（供支配树使用，稍后由
 *   [BlockExceptionHandler] 撤回）；
 * - [setupExitConnections] 把没有后继的块连到方法出口块。
 *
 * **Kotlin 转换说明**：本类既是访问器又有大量静态工具方法，故保持 `class` + `companion object`；
 * 静态方法加 `@JvmStatic` 以维持 Java 侧的静态调用。
 */
class BlockSplitter : AbstractVisitor() {

	companion object {
		/**
		 * 这些指令必须单独成块（不能与其他指令混在一个块里）。
		 */
		private val SEPARATE_INSNS: Set<InsnType> = EnumSet.of(
			InsnType.RETURN,
			InsnType.IF,
			InsnType.SWITCH,
			InsnType.MONITOR_ENTER,
			InsnType.MONITOR_EXIT,
			InsnType.THROW,
			InsnType.MOVE_EXCEPTION,
		)

		fun isSeparate(insnType: InsnType): Boolean = SEPARATE_INSNS.contains(insnType)

		/**
		 * 切分后不自动与下一块连接的指令（例如 return/throw/goto/分支）。
		 */
		private val SPLIT_WITHOUT_CONNECT: Set<InsnType> = EnumSet.of(
			InsnType.RETURN,
			InsnType.THROW,
			InsnType.GOTO,
			InsnType.IF,
			InsnType.SWITCH,
			InsnType.JAVA_JSR,
			InsnType.JAVA_RET,
		)

		private fun splitBasicBlocks(mth: MethodNode): Map<Int, BlockNode> {
			val enterBlock = startNewBlock(mth, -1)
			enterBlock.add(AFlag.MTH_ENTER_BLOCK)
			mth.enterBlock = enterBlock

			val exitBlock = startNewBlock(mth, -1)
			exitBlock.add(AFlag.MTH_EXIT_BLOCK)
			mth.exitBlock = exitBlock

			val blocksMap = HashMap<Int, BlockNode>()
			var curBlock = enterBlock
			var prevInsn: InsnNode? = null
			for (insn in checkNotNull(mth.instructions)) {
				if (insn == null) {
					continue
				}
				if (insn.type == InsnType.NOP && insn.isAttrStorageEmpty()) {
					continue
				}
				val insnOffset = insn.getOffset()
				if (prevInsn == null) {
					// 方法入口块之后的第一个块
					curBlock = connectNewBlock(mth, curBlock, insnOffset)
				} else {
					val prevType = prevInsn.type
					if (SPLIT_WITHOUT_CONNECT.contains(prevType)) {
						curBlock = startNewBlock(mth, insnOffset)
					} else if (isSeparate(prevType) ||
						isSeparate(insn.type) ||
						insn.contains(AFlag.TRY_ENTER) ||
						prevInsn.contains(AFlag.TRY_LEAVE) ||
						insn.contains(AType.EXC_HANDLER) ||
						isSplitByJump(prevInsn, insn) ||
						isDoWhile(blocksMap, curBlock, insn)
					) {
						curBlock = connectNewBlock(mth, curBlock, insnOffset)
					}
				}
				blocksMap[insnOffset] = curBlock
				curBlock.instructions.add(insn)
				prevInsn = insn
			}
			return blocksMap
		}

		/** 为 `if` 指令初始化 then/else 块。 */
		private fun initBlocksInTargetNodes(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				val lastInsn = BlockUtils.getLastInsn(block)
				if (lastInsn is TargetInsnNode) {
					lastInsn.initBlocks(block)
				}
			}
		}

		fun connectNewBlock(mth: MethodNode, block: BlockNode, offset: Int): BlockNode {
			val newBlock = startNewBlock(mth, offset)
			connect(block, newBlock)
			return newBlock
		}

		fun startNewBlock(mth: MethodNode, offset: Int): BlockNode {
			val blocks = checkNotNull(mth.basicBlocks) as MutableList<BlockNode>
			val block = BlockNode(mth.nextBlockCId, blocks.size, offset)
			blocks.add(block)
			return block
		}

		fun connect(from: BlockNode, to: BlockNode) {
			if (!from.successors.contains(to)) {
				(from.successors as MutableList<BlockNode>).add(to)
			}
			if (!to.predecessors.contains(from)) {
				(to.predecessors as MutableList<BlockNode>).add(from)
			}
		}

		fun removeConnection(from: BlockNode, to: BlockNode) {
			(from.successors as MutableList<BlockNode>).remove(to)
			(to.predecessors as MutableList<BlockNode>).remove(from)
		}

		fun removePredecessors(block: BlockNode) {
			for (pred in block.predecessors) {
				(pred.successors as MutableList<BlockNode>).remove(block)
			}
			(block.predecessors as MutableList<BlockNode>).clear()
		}

		fun replaceConnection(source: BlockNode, oldDest: BlockNode, newDest: BlockNode) {
			removeConnection(source, oldDest)
			connect(source, newDest)
			replaceTarget(source, oldDest, newDest)
		}

		fun insertBlockBetween(mth: MethodNode, source: BlockNode, target: BlockNode): BlockNode {
			val newBlock = startNewBlock(mth, target.startOffset)
			newBlock.add(AFlag.SYNTHETIC)
			removeConnection(source, target)
			connect(source, newBlock)
			connect(newBlock, target)
			replaceTarget(source, target, newBlock)
			source.updateCleanSuccessors()
			newBlock.updateCleanSuccessors()
			return newBlock
		}

		fun blockSplitTop(mth: MethodNode, block: BlockNode): BlockNode {
			val newBlock = startNewBlock(mth, block.startOffset)
			for (pred in ArrayList(block.predecessors)) {
				replaceConnection(pred, block, newBlock)
				pred.updateCleanSuccessors()
			}
			connect(newBlock, block)
			newBlock.updateCleanSuccessors()
			return newBlock
		}

		fun copyBlockData(from: BlockNode, to: BlockNode) {
			val toInsns = to.instructions
			for (insn in from.instructions) {
				toInsns.add(insn.copyWithoutSsa())
			}
			to.copyAttributesFrom(from)
		}

		fun copyBlocksTree(mth: MethodNode, blocks: List<BlockNode>): List<BlockNode> {
			val copyBlocks = ArrayList<BlockNode>(blocks.size)
			val map = HashMap<BlockNode, BlockNode>()
			for (block in blocks) {
				val newBlock = startNewBlock(mth, block.startOffset)
				copyBlockData(block, newBlock)
				copyBlocks.add(newBlock)
				map[block] = newBlock
			}
			for (block in blocks) {
				val newBlock = getNewBlock(block, map)
				for (successor in block.successors) {
					val newSuccessor = getNewBlock(successor, map)
					connect(newBlock, newSuccessor)
				}
			}
			return copyBlocks
		}

		private fun getNewBlock(block: BlockNode, map: Map<BlockNode, BlockNode>): BlockNode = map[block] ?: throw KadxRuntimeException("Copy blocks tree failed. Missing block for connection: $block")

		fun replaceTarget(source: BlockNode, oldTarget: BlockNode, newTarget: BlockNode) {
			val lastInsn = BlockUtils.getLastInsn(source)
			if (lastInsn is TargetInsnNode) {
				lastInsn.replaceTargetBlock(oldTarget, newTarget)
			}
		}

		private fun setupConnectionsFromJumps(mth: MethodNode, blocksMap: Map<Int, BlockNode>) {
			for (block in checkNotNull(mth.basicBlocks)) {
				for (insn in block.instructions) {
					val jumps = insn.getAll(AType.JUMP)
					for (jump in jumps) {
						val srcBlock = getBlock(jump.src, blocksMap)
						val thisBlock = getBlock(jump.dest, blocksMap)
						connect(srcBlock, thisBlock)
					}
				}
			}
		}

		/**
		 * 把异常处理器临时连到抛出块上。
		 * 这条临时边用于构建接近最终的支配树，之后会由 [BlockExceptionHandler] 撤回。
		 */
		private fun addTempConnectionsForExcHandlers(mth: MethodNode, blocksMap: Map<Int, BlockNode>) {
			if (mth.isNoExceptionHandlers()) {
				return
			}
			for (block in checkNotNull(mth.basicBlocks)) {
				for (insn in block.instructions) {
					val catchAttr = insn.get(AType.EXC_CATCH) ?: continue
					for (handler in catchAttr.handlers) {
						val handlerBlock = getBlock(handler.handlerOffset, blocksMap)
						if (!handlerBlock.contains(AType.TMP_EDGE)) {
							val preds = block.predecessors
							if (preds.isEmpty()) {
								throw KadxRuntimeException("Unexpected missing predecessor for block: $block")
							}
							val start = if (preds.size == 1) preds[0] else block
							if (!start.successors.contains(handlerBlock)) {
								connect(start, handlerBlock)
								handlerBlock.addAttr(TmpEdgeAttr(start))
							}
						}
					}
				}
			}
		}

		private fun setupExitConnections(mth: MethodNode) {
			val exitBlock = checkNotNull(mth.exitBlock)
			for (block in checkNotNull(mth.basicBlocks)) {
				if (block.successors.isEmpty() && block !== exitBlock) {
					connect(block, exitBlock)
					if (BlockUtils.checkLastInsnType(block, InsnType.RETURN)) {
						block.add(AFlag.RETURN)
					}
				}
			}
		}

		private fun isSplitByJump(prevInsn: InsnNode, currentInsn: InsnNode): Boolean {
			val pJumps = prevInsn.getAll(AType.JUMP)
			for (jump in pJumps) {
				if (jump.src == prevInsn.getOffset()) {
					return true
				}
			}
			val cJumps = currentInsn.getAll(AType.JUMP)
			for (jump in cJumps) {
				if (jump.dest == currentInsn.getOffset()) {
					return true
				}
			}
			return false
		}

		private fun isDoWhile(blocksMap: Map<Int, BlockNode>, curBlock: BlockNode, insn: InsnNode): Boolean {
			// 拆分 do-while 块（最后一条指令是 if，目标就是本块）
			if (insn.type != InsnType.IF) {
				return false
			}
			val ifs = insn as IfNode
			val targetBlock = blocksMap[ifs.getTarget()]
			return targetBlock === curBlock
		}

		private fun getBlock(offset: Int, blocksMap: Map<Int, BlockNode>): BlockNode = blocksMap[offset] ?: throw KadxRuntimeException("Missing block: $offset")

		/** 把 MOVE_MULTI（多寄存器批量 move）展开成多条普通 MOVE。 */
		private fun expandMoveMulti(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				val insnsList = block.instructions
				var len = insnsList.size
				var i = 0
				while (i < len) {
					val insn = insnsList[i]
					if (insn.type == InsnType.MOVE_MULTI) {
						val mvCount = insn.argsCount / 2
						for (j in 0 until mvCount) {
							val mv = InsnNode(InsnType.MOVE, 1)
							val startArg = j * 2
							mv.setResult(insn.getArg(startArg) as RegisterArg)
							mv.addArg(insn.getArg(startArg + 1))
							mv.copyAttributesFrom(insn)
							if (j == 0) {
								mv.setOffset(insn.getOffset())
								insnsList[i] = mv
							} else {
								insnsList.add(i + j, mv)
							}
						}
						i += mvCount - 1
						len = insnsList.size
					}
					i++
				}
			}
		}

		private fun removeJumpAttr(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				for (insn in block.instructions) {
					insn.remove(AType.JUMP)
				}
			}
		}

		private fun removeInsns(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				block.instructions.removeIf { insn ->
					if (!insn.isAttrStorageEmpty()) {
						return@removeIf false
					}
					val insnType = insn.type
					insnType == InsnType.GOTO || insnType == InsnType.NOP
				}
			}
		}

		fun detachMarkedBlocks(mth: MethodNode) {
			for (block in checkNotNull(mth.basicBlocks)) {
				if (block.contains(AFlag.REMOVE)) {
					detachBlock(block)
				}
			}
		}

		fun removeEmptyDetachedBlocks(mth: MethodNode): Boolean = (checkNotNull(mth.basicBlocks) as MutableList<BlockNode>).removeIf { block ->
			block.instructions.isEmpty() &&
				block.predecessors.isEmpty() &&
				block.successors.isEmpty() &&
				!block.contains(AFlag.MTH_ENTER_BLOCK) &&
				!block.contains(AFlag.MTH_EXIT_BLOCK)
		}

		fun removeEmptyBlock(block: BlockNode): Boolean {
			if (canRemoveBlock(block)) {
				if (block.successors.size == 1) {
					val successor = block.successors[0]
					for (pred in block.predecessors) {
						(pred.successors as MutableList<BlockNode>).remove(block)
						connect(pred, successor)
						replaceTarget(pred, block, successor)
						pred.updateCleanSuccessors()
					}
					removeConnection(block, successor)
				} else {
					for (pred in block.predecessors) {
						(pred.successors as MutableList<BlockNode>).remove(block)
						pred.updateCleanSuccessors()
					}
				}
				block.add(AFlag.REMOVE)
				(block.successors as MutableList<BlockNode>).clear()
				(block.predecessors as MutableList<BlockNode>).clear()
				return true
			}
			return false
		}

		private fun canRemoveBlock(block: BlockNode): Boolean = block.instructions.isEmpty() &&
			block.isAttrStorageEmpty() &&
			block.successors.size <= 1 &&
			!block.predecessors.isEmpty() &&
			!block.contains(AFlag.MTH_ENTER_BLOCK) &&
			!block.contains(AFlag.MTH_EXIT_BLOCK) &&
			!block.successors.contains(block) // 无自环

		fun collectSuccessors(startBlock: BlockNode, methodEnterBlock: BlockNode, toRemove: MutableSet<BlockNode>) {
			val stack = ArrayDeque<BlockNode>()
			stack.add(startBlock)
			while (!stack.isEmpty()) {
				val block = stack.pop()
				if (!toRemove.contains(block)) {
					toRemove.add(block)
					for (successor in block.successors) {
						if (successor !== methodEnterBlock && toRemove.containsAll(successor.predecessors)) {
							stack.push(successor)
						}
					}
				}
			}
		}

		fun detachBlock(block: BlockNode) {
			for (pred in block.predecessors) {
				(pred.successors as MutableList<BlockNode>).remove(block)
				pred.updateCleanSuccessors()
			}
			for (successor in block.successors) {
				(successor.predecessors as MutableList<BlockNode>).remove(block)
			}
			block.add(AFlag.REMOVE)
			(block.predecessors as MutableList<BlockNode>).clear()
			(block.successors as MutableList<BlockNode>).clear()
		}
	}

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		mth.initBasicBlocks()
		val blocksMap = splitBasicBlocks(mth)
		setupConnectionsFromJumps(mth, blocksMap)
		initBlocksInTargetNodes(mth)

		expandMoveMulti(mth)
		if (mth.contains(AFlag.RESOLVE_JAVA_JSR)) {
			ResolveJavaJSR.process(mth)
		}

		removeJumpAttr(mth)
		removeInsns(mth)
		removeEmptyDetachedBlocks(mth)
		(checkNotNull(mth.basicBlocks) as MutableList<BlockNode>).removeIf { removeEmptyBlock(it) }

		addTempConnectionsForExcHandlers(mth, blocksMap)
		setupExitConnections(mth)

		mth.updateBlockPositions()
		mth.unloadInsnArr()
	}
}
