package jadx.core.dex.visitors.regions.maker

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.EdgeInsnAttr
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.attributes.nodes.LoopLabelAttr
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.Edge
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.conditions.IfInfo
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.utils.BlockUtils
import jadx.core.utils.ListUtils
import jadx.core.utils.RegionUtils
import jadx.core.utils.blocks.BlockSet
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.LinkedList
import java.util.Queue

/**
 * 构建 `loop` 区域。
 *
 * **算法意图**：自然循环在 CFG 里由回边定义。本类：
 * 1. [makeLoopRegion] 从循环出口块里选出一个 if 头块，构造 [LoopRegion]；
 * 2. [process] 根据条件位置区分 `while`（条件在前）与 `do-while`（条件在后），
 *    并构建循环体、处理多出口（break/return）；
 * 3. [makeEndlessLoop] 处理 `while (true)`（无出口条件）；
 * 4. [validOutBlock] / [checkLoopExits] 校验出口块是否合法；
 * 5. [insertLoopBreak] / [insertContinue] 为跳转补 break/continue 指令。
 *
 * Kotlin 转换说明：
 * - 所有块/循环对象比较用 `===`；
 * - `loop.start/end/loopBlocks`、`edge.source/target` 等使用属性语法；
 * - 静态辅助方法放入 companion。
 */
internal class LoopRegionMaker(
	private val mth: MethodNode,
	private val regionMaker: RegionMaker,
	private val ifMaker: IfRegionMaker,
) {

	fun process(curRegion: IRegion, loop: LoopInfo, stack: RegionStack): BlockNode? {
		val loopStart = loop.start
		val exitBlocksSet = HashSet(loop.exitNodes)

		// 设定出口块扫描优先级
		// 当循环有多个出口（break/return）时有助于选择正确的头块
		val exitBlocks = ArrayList<BlockNode>(exitBlocksSet.size)
		val nextStart = BlockUtils.getNextBlock(loopStart)
		if (nextStart != null && exitBlocksSet.remove(nextStart)) {
			exitBlocks.add(nextStart)
		}
		if (exitBlocksSet.remove(loopStart)) {
			exitBlocks.add(loopStart)
		}
		if (exitBlocksSet.remove(loop.end)) {
			exitBlocks.add(loop.end)
		}
		exitBlocks.addAll(exitBlocksSet)

		val loopRegion = makeLoopRegion(curRegion, loop, exitBlocks)
		if (loopRegion == null) {
			val exit = makeEndlessLoop(curRegion, stack, loop, loopStart)
			insertContinue(loop)
			return exit
		}
		@Suppress("UNCHECKED_CAST")
		(curRegion.getSubBlocks() as MutableList<IContainer>).add(loopRegion)
		val outerRegion = stack.peekRegion()
		stack.push(loopRegion)

		var condInfo = ifMaker.buildIfInfo(loopRegion)
		val condThen = condInfo.thenBlock
		if (condThen == null || !loop.loopBlocks.contains(condThen)) {
			// 若 then 指向出口，则反转循环条件
			condInfo = IfInfo.invert(condInfo)
		}
		loopRegion.updateCondition(condInfo)
		// 防止 if 与循环条件合并
		for (b in condInfo.mergedBlocks) {
			b.add(AFlag.ADDED_TO_REGION)
		}
		exitBlocks.removeAll(condInfo.mergedBlocks.toList())

		if (exitBlocks.isNotEmpty()) {
			// 与循环条件相关的块
			val loopConditionBlocks = loopRegion.getConditionBlocks()

			for (exitEdge in loop.exitEdges) {
				val exitSource = exitEdge.source
				if (loopConditionBlocks.contains(exitSource)) {
					val outBlock = BlockUtils.followEmptyPath(exitEdge.target)
					for (pred in outBlock.getPredecessors()) {
						// 从“顶部”重新搜索出口边
						for (exitEdgeTop in loop.exitEdges) {
							if (!loopConditionBlocks.contains(exitEdgeTop.source)) {
								if (BlockUtils.isPathExists(exitEdgeTop.target, pred) || exitEdgeTop.target === outBlock) {
									insertLoopBreak(stack, loop, outBlock, exitEdgeTop.source, Edge(pred, outBlock))
								}
							}
						}
					}
					// 已找到出口边，不再继续检查
					break
				}
			}
		}

		val out: BlockNode?
		if (loopRegion.isConditionAtEnd()) {
			val thenBlock = condInfo.thenBlock
			val out0 = if (thenBlock === loop.end || thenBlock === loopStart) condInfo.elseBlock else thenBlock
			out = BlockUtils.followEmptyPath(checkNotNull(out0))
			loopStart.remove(AType.LOOP)
			loop.end.add(AFlag.ADDED_TO_REGION)
			stack.addExit(loop.end)
			regionMaker.clearBlockProcessedState(loopStart)
			val body = regionMaker.makeRegion(loopStart)
			loopRegion.setBody(body)
			loopStart.addAttr(AType.LOOP, loop)
			loop.end.remove(AFlag.ADDED_TO_REGION)
		} else {
			var out1: BlockNode? = condInfo.elseBlock
			if (outerRegion != null &&
				out1 != null &&
				out1.contains(AFlag.LOOP_START) &&
				!out1.getAll(AType.LOOP).contains(loop) &&
				RegionUtils.isRegionContainsBlock(outerRegion, out1)
			) {
				// 跳到已处理的外层循环
				out1 = null
			}
			stack.addExit(out1)
			val loopBody = condInfo.thenBlock
			val body: Region
			if (loopBody == loopStart) {
				// 空循环体
				body = Region(loopRegion)
			} else {
				body = regionMaker.makeRegion(checkNotNull(loopBody))
			}
			// 把从循环头到第一个条件块之间的块加入循环体
			val conditionBlock = condInfo.mergedBlocks.first
			if (loopStart !== conditionBlock) {
				val blocks = HashSet(BlockUtils.getAllPathsBlocks(loopStart, conditionBlock))
				blocks.remove(conditionBlock)
				for (block in blocks) {
					if (block.getInstructions().isEmpty() &&
						!block.contains(AFlag.ADDED_TO_REGION) &&
						!RegionUtils.isRegionContainsBlock(body, block)
					) {
						body.add(block)
					}
				}
			}
			loopRegion.setBody(body)
			out = out1
		}
		stack.pop()
		insertContinue(loop)
		return out
	}

	/**
	 * 从候选出口块中选出循环头块并构造 [LoopRegion]。
	 */
	private fun makeLoopRegion(curRegion: IRegion, loop: LoopInfo, exitBlocks: List<BlockNode>): LoopRegion? {
		for (block in exitBlocks) {
			// 忽略通向异常处理器的块
			if (block.contains(AType.EXC_HANDLER)) {
				continue
			}
			// 忽略不是 if 分支的块
			val lastInsn = BlockUtils.getLastInsn(block)
			if (lastInsn == null || lastInsn.type != InsnType.IF) {
				continue
			}
			// 跳过嵌套 if
			val loops = block.getAll(AType.LOOP)
			if (loops.isNotEmpty() && loops[0] !== loop) {
				continue
			}
			val exitAtLoopEnd = isExitAtLoopEnd(block, loop)

			val loopRegion = LoopRegion(curRegion, loop, block, exitAtLoopEnd)

			val found: Boolean
			if (block === loop.start || exitAtLoopEnd || BlockUtils.isEmptySimplePath(loop.start, block)) {
				found = true
			} else if (block.getPredecessors().contains(loop.start)) {
				loopRegion.setPreCondition(loop.start)
				// 若无法合并前置条件，则说明这不是正确的头块
				found = loopRegion.checkPreCondition()
			} else {
				found = false
			}
			var foundVar = found
			if (foundVar) {
				val list = mth.getAllLoopsForBlock(block)
				if (list.size >= 2) {
					// 若所有后继都跳出所有循环，则是坏条件
					var allOuter = true
					for (outerBlock in checkNotNull(block.getCleanSuccessors())) {
						val outLoopList = ArrayList(mth.getAllLoopsForBlock(outerBlock))
						outLoopList.remove(loop)
						if (outLoopList.isNotEmpty()) {
							// 进入外层循环
							allOuter = false
							break
						}
					}
					if (allOuter) {
						foundVar = false
					}
				}
			}
			if (foundVar && !checkLoopExits(loop, block)) {
				foundVar = false
			}
			if (foundVar) {
				return loopRegion
			}
		}
		// 未找到出口 => 无限循环
		return null
	}

	private fun isExitAtLoopEnd(exit: BlockNode, loop: LoopInfo): Boolean {
		val loopEnd = loop.end
		if (exit === loopEnd) {
			return true
		}
		val loopStart = loop.start
		if (loopStart.getInstructions().isEmpty() && ListUtils.isSingleElement(loopStart.getSuccessors(), exit)) {
			return false
		}
		return loopEnd.getInstructions().isEmpty() && ListUtils.isSingleElement(loopEnd.getPredecessors(), exit)
	}

	/**
	 * 检查把 mainExitBlock 当作头块时，出口是否与循环条件一致。
	 */
	private fun checkLoopExits(loop: LoopInfo, mainExitBlock: BlockNode): Boolean {
		val exitEdges = loop.exitEdges
		if (exitEdges.size < 2) {
			return true
		}
		// 若选出的头块没有出口边，则报错
		var mainExitEdge: Edge? = null
		for (edge in exitEdges) {
			if (edge.source === mainExitBlock) {
				mainExitEdge = edge
				break
			}
		}
		if (mainExitEdge == null) {
			throw JadxRuntimeException("Not found exit edge by exit block: " + mainExitBlock)
		}

		val mainOutBlock = mainExitEdge.target
		val firstWorkAfterMainExitBlock = BlockUtils.followEmptyPath(mainOutBlock)
		val firstInstructions = firstWorkAfterMainExitBlock.getInstructions()

		// 若从条件头有直接通往 return 的路径，则所有出口都在循环内
		if (firstInstructions.size == 1 && firstInstructions[0].type == InsnType.RETURN) {
			return true
		}

		// 否则出口必须通向合法的 out block
		return validOutBlock(firstWorkAfterMainExitBlock, loop)
	}

	/**
	 * 合法的 out block：每条出口路径要么经过它，要么不与任何其他出口路径相交
	 * （允许重复一个块）。
	 */
	private fun validOutBlock(outBlock: BlockNode, loop: LoopInfo): Boolean {
		val exitEdges = loop.exitEdges
		val edgesToCheck: Queue<Edge> = LinkedList(exitEdges)

		while (edgesToCheck.isNotEmpty()) {
			val exitEdge = edgesToCheck.remove()
			val exitBlock = exitEdge.target

			// 仅取沿 exitEdge 路径上的支配边界
			val dominanceFrontier: List<BlockNode>
			if (!exitEdge.isSynthetic()) {
				dominanceFrontier = BlockUtils.bitSetToBlocks(mth, BlockUtils.getDomFrontierThroughEdge(exitEdge))
			} else {
				dominanceFrontier = BlockUtils.bitSetToBlocks(mth, exitEdge.target.domFrontier)
			}

			if (outBlock.isDominator(exitBlock) || outBlock === exitBlock) {
				continue
			}

			for (crossing in dominanceFrontier) {
				if (crossing === outBlock) {
					continue
				}
				if (BlockUtils.isExitBlock(mth, crossing)) {
					continue
				}

				// 找交叉点之后第一条带指令的块
				var firstInstructionBlock = crossing
				val cInsns = crossing.getInstructions()
				if (cInsns.isEmpty()) {
					firstInstructionBlock = BlockUtils.followEmptyPath(crossing)
				}

				if (!(
						viaValidUncleanSuccessor(exitBlock, crossing, loop) ||
							noWorkBeforeEnd(firstInstructionBlock, outBlock) ||
							oneBlockOfWorkBeforeEnd(firstInstructionBlock, outBlock) ||
							isNestedIfCross(crossing, edgesToCheck) ||
							isOuterOutblock(crossing, loop)
						)
				) {
					return false
				}
			}
		}
		return true
	}
	private fun viaValidUncleanSuccessor(exitBlock: BlockNode, crossing: BlockNode, loop: LoopInfo): Boolean {
		// 若 exitBlock 到 crossing 有 clean 路径，则不适用
		if (BlockUtils.isPathExists(exitBlock, crossing)) {
			return false
		}

		if (crossing.contains(AFlag.LOOP_START)) {
			// 找到包含该 loop start 的外层循环
			var parent = loop.parentLoop
			var outerLoop: LoopInfo? = null
			while (parent != null) {
				if (parent.start === crossing) {
					outerLoop = parent
					break
				}
				parent = parent.parentLoop
			}

			if (outerLoop != null) {
				val loopEnd = outerLoop.end
				val predecessors = loopEnd.getPredecessors()
				if (predecessors.size > 1) {
					for (predecessor in predecessors) {
						// 若从 exit 可达的前驱无法插入 continue，则不接受
						if (BlockUtils.isPathExists(exitBlock, predecessor) &&
							!canInsertContinue(predecessor, predecessors, loopEnd, outerLoop.exitNodes)
						) {
							return false
						}
					}
				} else {
					return false
				}
			}
		}

		// 所有分支都有合法 continue，或目标不是 loop start（异常处理器）
		return true
	}

	private fun noWorkBeforeEnd(firstInstructionBlock: BlockNode, outBlock: BlockNode): Boolean = BlockUtils.isExitBlock(mth, firstInstructionBlock) || firstInstructionBlock === outBlock

	private fun oneBlockOfWorkBeforeEnd(firstInstructionBlock: BlockNode, outBlock: BlockNode): Boolean {
		val cleanSuccessors = checkNotNull(firstInstructionBlock.getCleanSuccessors())
		if (cleanSuccessors.isEmpty()) {
			return false
		}
		for (cleanSuccessor in cleanSuccessors) {
			val nextInstructionBlock = BlockUtils.followEmptyPath(cleanSuccessor)
			if (!BlockUtils.isExitBlock(mth, nextInstructionBlock) && nextInstructionBlock !== outBlock) {
				return false
			}
		}
		return true
	}

	private fun isNestedIfCross(crossing: BlockNode, edgesToCheck: Queue<Edge>): Boolean {
		val predecessors = crossing.getPredecessors()

		// 找一个支配其他所有前驱的前驱
		var possibleFirstIF = BlockUtils.followEmptyPath(predecessors[0], true)
		for (predecessor in predecessors) {
			val possibleIF = BlockUtils.followEmptyPath(predecessor, true)
			if (crossing.isDominator(possibleIF)) {
				possibleFirstIF = possibleIF
			}
		}

		// 若无法构造合并 if，则不适用
		val currentIf = IfRegionMaker.makeIfInfo(mth, possibleFirstIF) ?: return false
		val mergedIf = IfRegionMaker.mergeNestedIfNodes(currentIf) ?: return false

		val mergedBlocks = mergedIf.mergedBlocks
		for (predecessor in predecessors) {
			val possibleIF = BlockUtils.followEmptyPath(predecessor, true)
			if (!mergedBlocks.contains(possibleIF)) {
				return false
			}
		}

		// 若该交叉是合并 if 的结果，则继续检查下一个交叉
		val placeHolderEdge = Edge(crossing, crossing, true)
		if (!edgesToCheck.contains(placeHolderEdge)) {
			edgesToCheck.add(placeHolderEdge)
		}
		return true
	}

	private fun isOuterOutblock(crossing: BlockNode, loop: LoopInfo): Boolean {
		val edgeInsns = crossing.getAll(AType.EDGE_INSN)
		for (edgeInsn in edgeInsns) {
			val insn = edgeInsn.insn
			// 若有 break 边指令
			if (insn.type == InsnType.BREAK) {
				val loopsBrokenFrom = checkNotNull(insn.get(AType.LOOP)).list
				for (loopBrokenFrom in loopsBrokenFrom) {
					// 若 break 的是当前循环的某一层父循环
					if (loop.hasParent(loopBrokenFrom)) {
						val target = edgeInsn.end
						if (target === crossing) {
							return true
						}
					}
				}
			}
		}
		return false
	}
	private fun makeEndlessLoop(curRegion: IRegion, stack: RegionStack, loop: LoopInfo, loopStart: BlockNode): BlockNode? {
		val loopRegion = LoopRegion(curRegion, loop, null, false)
		@Suppress("UNCHECKED_CAST")
		(curRegion.getSubBlocks() as MutableList<IContainer>).add(loopRegion)

		loopStart.remove(AType.LOOP)
		regionMaker.clearBlockProcessedState(loopStart)
		stack.push(loopRegion)

		var out: BlockNode? = null
		// 为出口插入 break
		val exitEdges = loop.exitEdges
		if (exitEdges.size == 1) {
			val exitEdge = exitEdges[0]
			val exit = exitEdge.target
			if (insertLoopBreak(stack, loop, exit, exitEdge.source, exitEdge)) {
				val nextBlock = BlockUtils.getNextBlock(exit)
				if (nextBlock != null) {
					stack.addExit(nextBlock)
					out = nextBlock
				}
			}
		} else {
			loop@ for (exitEdge in exitEdges) {
				val exit = exitEdge.target
				val blocks = ArrayList(BlockUtils.bitSetToBlocks(mth, BlockUtils.getDomFrontierThroughEdge(exitEdge)))

				// 只有在没有其他合法 outblock 时才选择方法出口
				val methodExit = mth.exitBlock
				if (methodExit != null && blocks.contains(methodExit)) {
					blocks.remove(methodExit)
					blocks.add(methodExit)
				}
				for (block in blocks) {
					if (BlockUtils.isPathExists(exit, block)) {
						if (validOutBlock(block, loop)) {
							out = block
							break@loop
						}
					} else if (block.contains(AFlag.LOOP_START)) {
						// 外层循环回边之前没有汇合控制流的特殊情况
						if (validOutBlock(exit, loop)) {
							out = exit
							break@loop
						}
					}
				}
			}

			// 添加 break
			stack.addExit(out)
			if (out != null && out !== mth.exitBlock) {
				// 在每条从循环可达的入边上添加 break
				for (predecessor in out.getPredecessors()) {
					for (exitEdge in loop.exitEdges) {
						val target = exitEdge.target
						if (BlockUtils.isPathExists(target, predecessor) || target === out) {
							insertLoopBreak(stack, loop, out, exitEdge.source, Edge(predecessor, out))
						}
					}
				}
			}
		}

		val body = regionMaker.makeRegion(loopStart)
		val loopEnd = loop.end
		if (!RegionUtils.isRegionContainsBlock(body, loopEnd) &&
			!loopEnd.contains(AType.EXC_HANDLER) &&
			!inExceptionHandlerBlocks(loopEnd)
		) {
			@Suppress("UNCHECKED_CAST")
			(body.getSubBlocks() as MutableList<IContainer>).add(loopEnd)
		}
		loopRegion.setBody(body)

		if (out == null) {
			val next = BlockUtils.getNextBlock(loopEnd)
			out = if (RegionUtils.isRegionContainsBlock(body, next)) null else next
		}
		stack.pop()
		loopStart.addAttr(AType.LOOP, loop)
		return out
	}

	private fun inExceptionHandlerBlocks(loopEnd: BlockNode): Boolean {
		if (mth.exceptionHandlersCount == 0) {
			return false
		}
		for (eh in mth.getExceptionHandlers()) {
			if (eh.blocks.contains(loopEnd)) {
				return true
			}
		}
		return false
	}

	private fun canInsertBreak(exit: BlockNode): Boolean {
		if (BlockUtils.containsExitInsn(exit)) {
			return false
		}
		val simplePath = BlockUtils.buildSimplePath(exit)
		if (simplePath.isNotEmpty()) {
			val lastBlock = simplePath[simplePath.size - 1]
			if (lastBlock.isMthExitBlock() ||
				lastBlock.isReturnBlock() ||
				mth.isPreExitBlock(lastBlock)
			) {
				return false
			}
		}
		// 检查是否存在外层 switch
		val paths = BlockUtils.getAllPathsBlocks(checkNotNull(mth.enterBlock), exit)
		for (block in paths) {
			if (BlockUtils.checkLastInsnType(block, InsnType.SWITCH)) {
				return false
			}
		}
		return true
	}

	/**
	 * 在 exitEdge 与 loopExit 相交处插入 break 指令。
	 */
	private fun insertLoopBreak(
		stack: RegionStack,
		loop: LoopInfo,
		loopExit: BlockNode,
		blockOnLoop: BlockNode,
		exitEdge: Edge,
	): Boolean {
		var exit: BlockNode? = exitEdge.target
		var insertEdge: Edge? = null
		var confirm = false
		// 特殊情况 1：跳到外层循环
		val exitEnd = BlockUtils.followEmptyPath(checkNotNull(exit))
		val loops = exitEnd.getAll(AType.LOOP)
		for (loopAtEnd in loops) {
			if (loopAtEnd !== loop && loop.hasParent(loopAtEnd)) {
				insertEdge = exitEdge
				confirm = true
				break
			}
		}

		if (!confirm) {
			// 若目标是简单块（循环出口后的第一个节点），则从下一条边开始搜索
			val isSimple = BlockUtils.followEmptyPath(checkNotNull(exit)) !== exit
			var insertBlock: BlockNode? = if (isSimple) null else exitEdge.source
			val visited = BlockSet(mth)
			while (true) {
				if (exit == null || visited.contains(exit)) {
					break
				}
				visited.add(exit)
				if (insertBlock != null && BlockUtils.isPathExists(loopExit, exit)) {
					// 找到交叉
					if (canInsertBreak(insertBlock)) {
						insertEdge = Edge(insertBlock, exit)
						confirm = true
						break
					}
					return false
				}
				insertBlock = exit
				val cs = checkNotNull(exit.getCleanSuccessors())
				exit = if (cs.size == 1) cs[0] else null
			}
		}
		if (!confirm) {
			return false
		}
		val breakInsn = InsnNode(InsnType.BREAK, 0)
		breakInsn.addAttr(AType.LOOP, loop)
		EdgeInsnAttr.addEdgeInsn(checkNotNull(insertEdge), breakInsn)
		stack.addExit(exit)
		// 需要时给 break 加标签
		addBreakLabel(blockOnLoop, checkNotNull(exit), breakInsn)
		return true
	}

	/**
	 * 若从循环退出需要跨越多层循环，则给 break 指令加标签。
	 */
	private fun addBreakLabel(blockOnLoop: BlockNode, exit: BlockNode, breakInsn: InsnNode) {
		val exitLoop = mth.getAllLoopsForBlock(exit)
		if (exitLoop.isNotEmpty()) {
			return
		}
		val inLoops = mth.getAllLoopsForBlock(blockOnLoop)
		if (inLoops.size < 2) {
			return
		}
		// 查找父循环
		var parentLoop: LoopInfo? = null
		for (loop in inLoops) {
			if (loop.parentLoop == null) {
				parentLoop = loop
				break
			}
		}
		if (parentLoop == null) {
			return
		}
		if (parentLoop.end !== exit && !parentLoop.exitNodes.contains(exit)) {
			val labelAttr = LoopLabelAttr(parentLoop)
			breakInsn.addAttr(labelAttr)
			parentLoop.start.addAttr(labelAttr)
		}
	}

	companion object {
		private fun insertContinue(loop: LoopInfo) {
			val loopEnd = loop.end
			val predecessors = loopEnd.getPredecessors()
			if (predecessors.size <= 1) {
				return
			}
			val loopExitNodes = loop.exitNodes
			for (pred in predecessors) {
				if (canInsertContinue(pred, predecessors, loopEnd, loopExitNodes)) {
					val cont = InsnNode(InsnType.CONTINUE, 0)
					pred.instructions.add(cont)
				}
			}
		}

		private fun canInsertContinue(
			pred: BlockNode,
			predecessors: List<BlockNode>,
			loopEnd: BlockNode,
			loopExitNodes: Set<BlockNode>,
		): Boolean {
			if (!pred.contains(AFlag.SYNTHETIC) ||
				BlockUtils.checkLastInsnType(pred, InsnType.CONTINUE)
			) {
				return false
			}
			val preds = pred.getPredecessors()
			if (preds.isEmpty()) {
				return false
			}
			val codePred = preds[0]
			if (codePred.contains(AFlag.ADDED_TO_REGION)) {
				return false
			}
			if (loopEnd.isDominator(codePred) || loopExitNodes.contains(codePred)) {
				return false
			}
			if (isDominatedOnBlocks(codePred, predecessors)) {
				return false
			}
			if (pred.getAll(AType.EDGE_INSN).isNotEmpty()) {
				// 若已插入 break，则不要在同一点再插 continue
				val insns = pred.getAll(AType.EDGE_INSN)
				for (insn in insns) {
					if (insn.insn.type == InsnType.BREAK) {
						return false
					}
				}
			}
			var gotoExit = false
			for (exit in loopExitNodes) {
				if (BlockUtils.isPathExists(codePred, exit)) {
					gotoExit = true
					break
				}
			}
			return gotoExit
		}

		private fun isDominatedOnBlocks(dom: BlockNode, blocks: List<BlockNode>): Boolean {
			for (node in blocks) {
				if (!node.isDominator(dom)) {
					return false
				}
			}
			return true
		}
	}
}
