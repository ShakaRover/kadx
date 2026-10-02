package jadx.core.dex.visitors.regions.maker

import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.EdgeInsnAttr
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnContainer
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.conditions.IfCondition
import jadx.core.dex.regions.conditions.IfInfo
import jadx.core.dex.regions.conditions.IfRegion
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.trycatch.ExcHandlerAttr
import jadx.core.utils.BlockUtils
import jadx.core.utils.blocks.BlockSet
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 构建 `if` 区域。
 *
 * **算法意图**：`if` 在字节码里是一串条件跳转，本类负责：
 * - [makeIfInfo] 把一条 if 指令封装成 [IfInfo]；
 * - [mergeNestedIfNodes] 把嵌套 if 合并成 `&&` / `||` / 三目条件；
 * - [restructureIf] 选择 then/else/out 块，并判断分支是否“坏”；
 * - [findOutBlock] 用支配边界求 then/else 的汇合出口块。
 *
 * Kotlin 转换说明：
 * - 静态辅助方法放入 companion + `@JvmStatic`（LoopRegionMaker 会调用）；
 * - 所有块对象比较用 `===`（原 Java 的 `==`）；
 * - 可空块参数如实标注，保持原有判空逻辑。
 */
internal class IfRegionMaker(private val mth: MethodNode, private val regionMaker: RegionMaker) {

	fun process(currentRegion: IRegion, block: BlockNode, ifnode: IfNode, stack: RegionStack): BlockNode? {
		if (block.contains(AFlag.ADDED_TO_REGION)) {
			// 该块已属于其他 if 区域
			return ifnode.getThenBlock()
		}
		var currentIf = makeIfInfo(mth, block) ?: return null
		val mergedIf = mergeNestedIfNodes(currentIf)
		if (mergedIf != null) {
			currentIf = mergedIf
		} else {
			// 反转简单条件（编译器常这么做）；每个块只反转一次
			if (!block.contains(AFlag.DONT_INVERT)) {
				currentIf = IfInfo.invert(currentIf)
				block.add(AFlag.DONT_INVERT)
			}
		}
		val modifiedIf = restructureIf(block, currentIf)
		if (modifiedIf != null) {
			currentIf = modifiedIf
		} else {
			if (currentIf.getMergedBlocks().size() <= 1) {
				return null
			}
			currentIf = makeIfInfo(mth, block) ?: return null
			currentIf = restructureIf(block, currentIf) ?: return null
		}
		confirmMerge(currentIf)

		val ifRegion = IfRegion(currentRegion)
		ifRegion.updateCondition(currentIf)
		@Suppress("UNCHECKED_CAST")
		(currentRegion.getSubBlocks() as MutableList<IContainer>).add(ifRegion)

		val outBlock = currentIf.getOutBlock()
		stack.push(ifRegion)
		stack.addExit(outBlock)

		val thenBlock = currentIf.getThenBlock()
		if (thenBlock == null) {
			// 空的 then 块，虽不常见但可能合法
			ifRegion.setThenRegion(Region(ifRegion))
		} else {
			ifRegion.setThenRegion(regionMaker.makeRegion(thenBlock))
		}
		val elseBlock = currentIf.getElseBlock()
		if (elseBlock == null || stack.containsExit(elseBlock)) {
			ifRegion.setElseRegion(null)
		} else {
			ifRegion.setElseRegion(regionMaker.makeRegion(elseBlock))
		}

		// 在新的 else 分支插入边指令
		if (ifRegion.getElseRegion() == null && outBlock != null) {
			val edgeInsnAttrs = outBlock.getAll(AType.EDGE_INSN)
			if (edgeInsnAttrs.isNotEmpty()) {
				val instructions = ArrayList<InsnNode>()
				for (edgeInsnAttr in edgeInsnAttrs) {
					if (edgeInsnAttr.end == outBlock) {
						if (currentIf.getMergedBlocks().contains(BlockUtils.followEmptyPath(edgeInsnAttr.start, true))) {
							instructions.add(edgeInsnAttr.insn)
						}
					}
				}
				if (instructions.isNotEmpty()) {
					val elseRegion = Region(ifRegion)
					elseRegion.add(InsnContainer(instructions))
					ifRegion.setElseRegion(elseRegion)
				}
			}
		}

		stack.pop()
		return outBlock
	}

	fun buildIfInfo(loopRegion: LoopRegion): IfInfo {
		var condInfo = makeIfInfo(mth, checkNotNull(loopRegion.getHeader()))
		condInfo = searchNestedIf(checkNotNull(condInfo))
		confirmMerge(condInfo)
		return condInfo
	}

	fun restructureIf(block: BlockNode, info0: IfInfo): IfInfo? {
		var info = info0
		val thenBlock = info.getThenBlock()
		val elseBlock = info.getElseBlock()

		if (thenBlock == elseBlock) {
			val ifInfo = IfInfo(info, null, null)
			ifInfo.setOutBlock(thenBlock)
			return ifInfo
		}

		// 选择 then / else / exit 块
		if (checkNotNull(thenBlock).contains(AFlag.RETURN) && checkNotNull(elseBlock).contains(AFlag.RETURN)) {
			info.setOutBlock(null)
			return info
		}
		// 初始化 outBlock（后续 isBadBranchBlock 会用到）
		info.setOutBlock(findOutBlock(mth, thenBlock, elseBlock))

		val badThen = isBadBranchBlock(info, checkNotNull(thenBlock))
		val badElse = isBadBranchBlock(info, checkNotNull(elseBlock))
		if (badThen && badElse) {
			if (Consts.DEBUG_RESTRUCTURE) {
				LOG.debug("Stop processing blocks after 'if': {}, method: {}", info.getMergedBlocks(), mth)
			}
			return null
		}
		if (badElse) {
			info = IfInfo(info, thenBlock, null)
			info.setOutBlock(elseBlock)
		} else if (badThen) {
			info = IfInfo.invert(info)
			info = IfInfo(info, elseBlock, null)
			info.setOutBlock(thenBlock)
		}

		// getPathCross 可能找不到 outBlock（例如一个分支有 return），需进一步检查
		if (info.getOutBlock() == null) {
			val scopeOutBlockThen = findScopeOutBlock(info.getThenBlock())
			val scopeOutBlockElse = findScopeOutBlock(info.getElseBlock())
			if (scopeOutBlockThen == null && scopeOutBlockElse != null) {
				info.setOutBlock(scopeOutBlockElse)
			} else if (scopeOutBlockThen != null && scopeOutBlockElse == null) {
				info.setOutBlock(scopeOutBlockThen)
			} else if (scopeOutBlockThen != null && scopeOutBlockThen === scopeOutBlockElse) {
				info.setOutBlock(scopeOutBlockThen)
			}
		}

		if (BlockUtils.isBackEdge(block, info.getOutBlock())) {
			info.setOutBlock(null)
		}
		return info
	}

	/**
	 * 若 startBlock 处于 (try) 作用域内，找出作用域结束块作为 outBlock。
	 */
	fun findScopeOutBlock(startBlock: BlockNode?): BlockNode? {
		if (startBlock == null) {
			return null
		}
		val domFrontiers = BlockUtils.bitSetToBlocks(mth, startBlock.domFrontier)
		var scopeOutBlock: BlockNode? = null

		// 从支配边界里找异常处理器；若其 topSplitter 支配分支块，则分支应在此结束
		for (domFrontier in domFrontiers) {
			val handler = domFrontier.get(AType.EXC_HANDLER)
			if (handler == null) {
				continue
			}
			val topSplitter = handler.getTryBlock()?.getTopSplitter()
			if (topSplitter != null && startBlock.isDominator(topSplitter)) {
				scopeOutBlock = BlockUtils.getTryAndHandlerCrossBlock(mth, handler.getHandler())
				break
			}
		}
		if (scopeOutBlock != null) {
			// 检查 outBlock 是否仍在 exit 块限制的作用域内
			for (exit in regionMaker.getStack().getExits()) {
				if (BlockUtils.isPathExists(exit, scopeOutBlock)) {
					return null
				}
			}
		}
		return scopeOutBlock
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(IfRegionMaker::class.java)

		@JvmStatic
		fun makeIfInfo(mth: MethodNode, ifBlock: BlockNode): IfInfo? {
			val lastInsn = BlockUtils.getLastInsn(ifBlock)
			if (lastInsn == null || lastInsn.getType() != InsnType.IF) {
				return null
			}
			val ifNode = lastInsn as IfNode
			val condition = IfCondition.fromIfNode(ifNode)
			val info = IfInfo(mth, condition, ifNode.getThenBlock(), ifNode.getElseBlock())
			info.getMergedBlocks().add(ifBlock)
			return info
		}

		@JvmStatic
		fun searchNestedIf(info: IfInfo): IfInfo {
			val next = mergeNestedIfNodes(info)
			if (next != null) {
				return next
			}
			return info
		}

		@JvmStatic
		fun findOutBlock(mth: MethodNode, thenBlock: BlockNode?, elseBlock: BlockNode?): BlockNode? {
			if (thenBlock === elseBlock) {
				return thenBlock
			}
			if (thenBlock == null || elseBlock == null) {
				return null
			}

			val thenDomFrontier = BlockUtils.newBlocksBitSet(mth)
			thenDomFrontier.or(checkNotNull(thenBlock.domFrontier))
			thenDomFrontier.set(thenBlock.pos)

			val elseDomFrontier = BlockUtils.newBlocksBitSet(mth)
			elseDomFrontier.or(checkNotNull(elseBlock.domFrontier))
			elseDomFrontier.set(elseBlock.pos)

			val intersection = BlockUtils.newBlocksBitSet(mth)
			intersection.or(thenDomFrontier)
			intersection.and(elseDomFrontier)
			intersection.clear(checkNotNull(mth.exitBlock).pos)
			val oneBlock = BlockUtils.bitSetToOneBlock(mth, intersection)
			if (oneBlock != null) {
				return oneBlock
			}

			val union = BlockUtils.newBlocksBitSet(mth)
			union.or(checkNotNull(thenBlock.domFrontier))
			union.or(checkNotNull(elseBlock.domFrontier))
			union.clear(checkNotNull(mth.exitBlock).pos)

			val candidates = BlockUtils.newBlocksBitSet(mth)
			for (candidate in BlockUtils.bitSetToBlocks(mth, union)) {
				if (isCandidateForOutBlock(mth, thenBlock, elseBlock, candidate)) {
					candidates.set(candidate.pos)
				}
			}

			val bottom = BlockUtils.getBottomBlock(BlockUtils.bitSetToBlocks(mth, candidates), true)
			if (bottom != null) {
				return bottom
			}

			// 回退：再次使用路径交叉
			return BlockUtils.getPathCross(mth, thenBlock, elseBlock)
		}

		@JvmStatic
		fun isCandidateForOutBlock(mth: MethodNode, thenBlock: BlockNode, elseBlock: BlockNode, candidate: BlockNode): Boolean {
			if (candidate.getPredecessors().size < 2) {
				return false
			}

			val coverageThenPreds = BlockUtils.newBlocksBitSet(mth)
			val coverageElsePreds = BlockUtils.newBlocksBitSet(mth)

			if (candidate === elseBlock) {
				coverageElsePreds.set(candidate.pos)
			}
			if (candidate === thenBlock) {
				coverageThenPreds.set(candidate.pos)
			}

			for (pred in candidate.getPredecessors()) {
				if (BlockUtils.isPathExists(thenBlock, pred)) {
					coverageThenPreds.set(pred.pos)
				}
				if (BlockUtils.isPathExists(elseBlock, pred)) {
					coverageElsePreds.set(pred.pos)
				}
			}
			if (coverageElsePreds.cardinality() == 0 || coverageThenPreds.cardinality() == 0) {
				return false
			}

			val coverageElsePred = BlockUtils.bitSetToOneBlock(mth, coverageElsePreds)
			val coverageThenPred = BlockUtils.bitSetToOneBlock(mth, coverageThenPreds)
			if (coverageElsePred != null && coverageElsePred === coverageThenPred) {
				return false
			}
			return true
		}

		private fun isBadBranchBlock(info: IfInfo, block: BlockNode): Boolean {
			// 检查块是否位于循环回边末尾
			if (block.contains(AFlag.LOOP_START) && block.getPredecessors().size == 1) {
				val pred = block.getPredecessors()[0]
				if (pred.contains(AFlag.LOOP_END)) {
					val startLoops = block.getAll(AType.LOOP)
					val endLoops = pred.getAll(AType.LOOP)
					for (startLoop in startLoops) {
						for (endLoop in endLoops) {
							if (startLoop === endLoop) {
								return true
							}
						}
					}
				}
			}
			// 若分支块本身就是 outBlock
			if (info.getOutBlock() != null) {
				return block === info.getOutBlock()
			}
			return !allPathsFromIf(block, info)
		}

		private fun allPathsFromIf(block: BlockNode, info: IfInfo): Boolean {
			val preds = block.getPredecessors()
			val ifBlocks = info.getMergedBlocks()
			for (pred in preds) {
				if (pred.contains(AFlag.LOOP_END)) {
					// 忽略循环回边
					continue
				}
				val top = BlockUtils.skipSyntheticPredecessor(pred)
				if (!ifBlocks.contains(top)) {
					return false
				}
			}
			return true
		}

		@JvmStatic
		fun mergeNestedIfNodes(currentIf: IfInfo): IfInfo? {
			val curThen0 = currentIf.getThenBlock()
			val curElse0 = currentIf.getElseBlock()
			if (curThen0 == curElse0) {
				return null
			}
			val curThen = checkNotNull(curThen0)
			val curElse = checkNotNull(curElse0)
			if (BlockUtils.isFollowBackEdge(curThen) || BlockUtils.isFollowBackEdge(curElse)) {
				return null
			}
			val nextIf: IfInfo
			val followThenBranch: Boolean
			val nextIfThen = getNextIf(currentIf, curThen)
			if (nextIfThen != null) {
				nextIf = nextIfThen
				followThenBranch = true
			} else {
				val nextIfElse = getNextIf(currentIf, curElse)
				if (nextIfElse != null) {
					nextIf = nextIfElse
					followThenBranch = false
				} else {
					return null
				}
			}
			var nextIfVar = nextIf

			val assignInlineNeeded = nextIfVar.getForceInlineInsns().isNotEmpty()
			if (assignInlineNeeded) {
				for (mergedBlock in currentIf.getMergedBlocks()) {
					if (mergedBlock.contains(AFlag.LOOP_START)) {
						// 不要把赋值内联进循环条件
						return currentIf
					}
				}
			}

			if (isInversionNeeded(currentIf, nextIfVar)) {
				nextIfVar = IfInfo.invert(nextIfVar)
			}
			val thenPathSame = BlockUtils.isEqualPaths(curThen, nextIfVar.getThenBlock())
			val elsePathSame = BlockUtils.isEqualPaths(curElse, nextIfVar.getElseBlock())
			if (!thenPathSame && !elsePathSame) {
				// 复杂条件，做额外检查
				if (checkConditionBranches(curThen, curElse) || checkConditionBranches(curElse, curThen)) {
					return null
				}
				var otherBranchBlock = if (followThenBranch) curElse else curThen
				otherBranchBlock = BlockUtils.followEmptyPath(otherBranchBlock)
				if (!BlockUtils.isPathExists(nextIfVar.getMergedBlocks().getFirst(), otherBranchBlock)) {
					return checkForTernaryInCondition(currentIf)
				}

				val tmpIf = mergeNestedIfNodes(nextIfVar)
				if (tmpIf != null) {
					nextIfVar = tmpIf
					if (isInversionNeeded(currentIf, nextIfVar)) {
						nextIfVar = IfInfo.invert(nextIfVar)
					}
					if (!canMerge(currentIf, nextIfVar, followThenBranch)) {
						return currentIf
					}
				} else {
					return currentIf
				}
			} else {
				if (assignInlineNeeded) {
					val sameOuts = (thenPathSame && !followThenBranch) || (elsePathSame && followThenBranch)
					if (!sameOuts) {
						currentIf.resetForceInlineInsns()
						return currentIf
					}
				}
			}

			val result = mergeIfInfo(currentIf, nextIfVar, followThenBranch)
			return searchNestedIf(result)
		}

		private fun checkForTernaryInCondition(currentIf: IfInfo): IfInfo? {
			val nextThen0 = getNextIf(currentIf, checkNotNull(currentIf.getThenBlock()))
			val nextElse0 = getNextIf(currentIf, checkNotNull(currentIf.getElseBlock()))
			if (nextThen0 == null || nextElse0 == null) {
				return null
			}
			if (checkNotNull(nextThen0.getMergedBlocks().getFirst().domFrontier) !=
				checkNotNull(nextElse0.getMergedBlocks().getFirst().domFrontier)
			) {
				return null
			}
			var nextThen = searchNestedIf(nextThen0)
			var nextElse = searchNestedIf(nextElse0)
			if (nextThen.getThenBlock() === nextElse.getThenBlock() &&
				nextThen.getElseBlock() === nextElse.getElseBlock()
			) {
				return mergeTernaryConditions(currentIf, nextThen, nextElse)
			}
			if (nextThen.getThenBlock() === nextElse.getElseBlock() &&
				nextThen.getElseBlock() === nextElse.getThenBlock()
			) {
				nextElse = IfInfo.invert(nextElse)
				return mergeTernaryConditions(currentIf, nextThen, nextElse)
			}
			return null
		}

		private fun mergeTernaryConditions(currentIf: IfInfo, nextThen: IfInfo, nextElse: IfInfo): IfInfo {
			val newCondition = IfCondition.ternary(
				currentIf.getCondition(),
				nextThen.getCondition(),
				nextElse.getCondition(),
			)
			val result = IfInfo(currentIf.getMth(), newCondition, nextThen.getThenBlock(), nextThen.getElseBlock())
			result.merge(currentIf, nextThen, nextElse)
			confirmMerge(result)
			return result
		}

		private fun isInversionNeeded(currentIf: IfInfo, nextIf: IfInfo): Boolean = BlockUtils.isEqualPaths(currentIf.getElseBlock(), nextIf.getThenBlock()) ||
			BlockUtils.isEqualPaths(currentIf.getThenBlock(), nextIf.getElseBlock())

		private fun canMerge(a: IfInfo, b: IfInfo, followThenBranch: Boolean): Boolean = if (followThenBranch) {
			BlockUtils.isEqualPaths(a.getElseBlock(), b.getElseBlock())
		} else {
			BlockUtils.isEqualPaths(a.getThenBlock(), b.getThenBlock())
		}

		private fun checkConditionBranches(from: BlockNode, to: BlockNode): Boolean {
			val cs = checkNotNull(from.getCleanSuccessors())
			return cs.size == 1 && cs.contains(to)
		}

		@JvmStatic
		fun mergeIfInfo(first: IfInfo, second: IfInfo, followThenBranch: Boolean): IfInfo {
			val mth = first.getMth()
			val skipBlocks = first.getSkipBlocks()
			val thenBlock: BlockNode?
			val elseBlock: BlockNode?
			if (followThenBranch) {
				thenBlock = second.getThenBlock()
				elseBlock = getBranchBlock(first.getElseBlock(), second.getElseBlock(), skipBlocks, mth)
			} else {
				thenBlock = getBranchBlock(first.getThenBlock(), second.getThenBlock(), skipBlocks, mth)
				elseBlock = second.getElseBlock()
			}
			val mergeOperation = if (followThenBranch) IfCondition.Mode.AND else IfCondition.Mode.OR
			val condition = IfCondition.merge(mergeOperation, first.getCondition(), second.getCondition())
			val result = IfInfo(mth, condition, thenBlock, elseBlock)
			result.merge(first, second)
			return result
		}

		private fun getBranchBlock(
			first0: BlockNode?,
			second0: BlockNode?,
			skipBlocks: MutableSet<BlockNode>,
			mth: MethodNode,
		): BlockNode? {
			if (first0 === second0) {
				return second0
			}
			val first = checkNotNull(first0)
			val second = checkNotNull(second0)
			if (BlockUtils.isEqualReturnBlocks(first, second)) {
				skipBlocks.add(first)
				return second
			}
			if (BlockUtils.isDuplicateBlockPath(first, second)) {
				first.add(AFlag.REMOVE)
				skipBlocks.add(first)
				return second
			}
			val cross = BlockUtils.getPathCross(mth, first, second)
			if (cross != null) {
				BlockUtils.visitBlocksOnPath(mth, first, cross) { skipBlocks.add(it) }
				BlockUtils.visitBlocksOnPath(mth, second, cross) { skipBlocks.add(it) }
				skipBlocks.remove(cross)
				return cross
			}
			val firstSkip = BlockUtils.followEmptyPath(first)
			val secondSkip = BlockUtils.followEmptyPath(second)
			if (firstSkip == secondSkip || BlockUtils.isEqualReturnBlocks(firstSkip, secondSkip)) {
				skipBlocks.add(first)
				skipBlocks.add(second)
				BlockUtils.visitBlocksOnEmptyPath(first) { skipBlocks.add(it) }
				BlockUtils.visitBlocksOnEmptyPath(second) { skipBlocks.add(it) }
				return secondSkip
			}
			throw JadxRuntimeException("Unexpected merge pattern")
		}

		@JvmStatic
		fun confirmMerge(info: IfInfo) {
			if (info.getMergedBlocks().size() > 1) {
				for (block in info.getMergedBlocks()) {
					if (block !== info.getMergedBlocks().getFirst()) {
						block.add(AFlag.ADDED_TO_REGION)
					}
				}
			}
			if (info.getSkipBlocks().isNotEmpty()) {
				for (block in info.getSkipBlocks()) {
					block.add(AFlag.ADDED_TO_REGION)
				}
				info.getSkipBlocks().clear()
			}
			for (forceInlineInsn in info.getForceInlineInsns()) {
				forceInlineInsn.add(AFlag.FORCE_ASSIGN_INLINE)
			}
		}
		private fun getNextIf(info: IfInfo, block: BlockNode): IfInfo? {
			if (!canSelectNext(info, block)) {
				return null
			}
			return getNextIfNodeInfo(info, block)
		}

		private fun canSelectNext(info: IfInfo, block: BlockNode): Boolean {
			if (block.getPredecessors().size == 1) {
				return true
			}
			return info.getMergedBlocks().containsAll(block.getPredecessors())
		}

		private fun getNextIfNodeInfo(info: IfInfo, block: BlockNode?): IfInfo? {
			if (block == null || block.contains(AType.LOOP) || block.contains(AFlag.ADDED_TO_REGION)) {
				return null
			}
			val lastInsn = BlockUtils.getLastInsn(block)
			if (lastInsn != null && lastInsn.getType() == InsnType.IF) {
				return makeIfInfo(info.getMth(), block)
			}
			val next = getNextBlockInIfSuccessorChain(block) ?: return null
			if (next.getPredecessors().size != 1 || next.contains(AFlag.ADDED_TO_REGION)) {
				return null
			}
			val forceInlineInsns = ArrayList<InsnNode>()
			if (!checkInsnsInline(block, next, forceInlineInsns)) {
				return null
			}
			val nextInfo = makeIfInfo(info.getMth(), next)
			if (nextInfo == null) {
				return getNextIfNodeInfo(info, next)
			}
			nextInfo.addInsnsForForcedInline(forceInlineInsns)
			return nextInfo
		}

		/**
		 * 允许单一后继，或两个后继中一个是 EXC_BOTTOM_SPLITTER 的情况。
		 */
		private fun getNextBlockInIfSuccessorChain(block: BlockNode): BlockNode? {
			val successors = block.getSuccessors()
			if (successors.size > 2 || successors.isEmpty()) {
				return null
			}
			val first = successors[0]
			if (successors.size == 1) {
				return first
			}
			val second = successors[1]
			val firstIsHandlerPath = first.contains(AFlag.EXC_BOTTOM_SPLITTER)
			val secondIsHandlerPath = second.contains(AFlag.EXC_BOTTOM_SPLITTER)
			if (!firstIsHandlerPath && !secondIsHandlerPath) {
				return null
			}
			if (firstIsHandlerPath && secondIsHandlerPath) {
				return null
			}
			val candidate = if (firstIsHandlerPath) second else first

			// 只要后继块没有指令，就继续向后递归
			if (candidate.getInstructions().isEmpty()) {
				return getNextBlockInIfSuccessorChain(candidate)
			}
			return candidate
		}

		/** 检查所有指令是否都能内联 */
		private fun checkInsnsInline(block: BlockNode, next: BlockNode, forceInlineInsns: MutableList<InsnNode>): Boolean {
			val insns = block.getInstructions()
			if (insns.isEmpty()) {
				return true
			}
			var pass = true
			for (insn in insns) {
				val res = insn.getResult() ?: return false
				val useList = checkNotNull(res.sVar).getUseList()
				val useCount = useList.size
				if (useCount == 0) {
					return false
				}
				val arg = useList[0]
				val usePlace = checkNotNull(arg.getParentInsn())
				if (!BlockUtils.blockContains(block, usePlace) && !BlockUtils.blockContains(next, usePlace)) {
					return false
				}
				if (useCount > 1) {
					forceInlineInsns.add(insn)
				} else {
					// 只允许强制赋值内联
					pass = false
				}
			}
			return pass
		}
	}
}
