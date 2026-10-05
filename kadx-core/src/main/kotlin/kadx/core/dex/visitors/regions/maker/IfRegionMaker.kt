package kadx.core.dex.visitors.regions.maker

import kadx.core.Consts
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.EdgeInsnAttr
import kadx.core.dex.attributes.nodes.LoopInfo
import kadx.core.dex.instructions.IfNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnContainer
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import java.util.BitSet
import kadx.core.dex.regions.Region
import kadx.core.dex.regions.conditions.IfCondition
import kadx.core.dex.regions.conditions.IfInfo
import kadx.core.dex.regions.conditions.IfRegion
import kadx.core.dex.regions.loops.LoopRegion
import kadx.core.dex.trycatch.ExcHandlerAttr
import kadx.core.utils.BlockUtils
import kadx.core.utils.blocks.BlockSet
import kadx.core.utils.exceptions.KadxRuntimeException
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
			if (currentIf.mergedBlocks.size() <= 1) {
				return null
			}
			currentIf = makeIfInfo(mth, block) ?: return null
			currentIf = restructureIf(block, currentIf) ?: return null
		}
		confirmMerge(currentIf)

		val ifRegion = IfRegion(currentRegion)
		ifRegion.updateCondition(currentIf)
		@Suppress("UNCHECKED_CAST")
		(currentRegion.subBlocks as MutableList<IContainer>).add(ifRegion)

		val outBlock = currentIf.getOutBlock()
		stack.push(ifRegion)
		stack.addExit(outBlock)

		val thenBlock = currentIf.thenBlock
		if (thenBlock == null) {
			// 空的 then 块，虽不常见但可能合法
			ifRegion.setThenRegion(Region(ifRegion))
		} else {
			ifRegion.setThenRegion(regionMaker.makeRegion(thenBlock))
		}
		val elseBlock = currentIf.elseBlock
		if (elseBlock == null || stack.containsExit(elseBlock)) {
			ifRegion.setElseRegion(null)
		} else {
			ifRegion.setElseRegion(regionMaker.makeRegion(elseBlock))
		}

		// 在新的 else 分支插入边指令
		if (ifRegion.elseRegion == null && outBlock != null) {
			val edgeInsnAttrs = outBlock.getAll(AType.EDGE_INSN)
			if (edgeInsnAttrs.isNotEmpty()) {
				val instructions = ArrayList<InsnNode>()
				for (edgeInsnAttr in edgeInsnAttrs) {
					if (edgeInsnAttr.end == outBlock) {
						if (currentIf.mergedBlocks.contains(BlockUtils.followEmptyPath(edgeInsnAttr.start, true))) {
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
		var condInfo = makeIfInfo(mth, checkNotNull(loopRegion.header))
		condInfo = searchNestedIf(checkNotNull(condInfo))
		confirmMerge(condInfo)
		return condInfo
	}

	fun restructureIf(block: BlockNode, info0: IfInfo): IfInfo? {
		var info = info0
		val thenBlock = info.thenBlock
		val elseBlock = info.elseBlock

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
				LOG.debug("Stop processing blocks after 'if': {}, method: {}", info.mergedBlocks, mth)
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
		// Attempt four: 仍无 outBlock 时，按 try 作用域边界回退（上游 #2791）
		if (info.getOutBlock() == null) {
			val scopeOutBlockThen = findScopeOutBlock(info.thenBlock)
			val scopeOutBlockElse = findScopeOutBlock(info.elseBlock)
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
			val topSplitter = handler.tryBlock?.getTopSplitter()
			if (topSplitter != null && startBlock.isDominator(topSplitter)) {
				scopeOutBlock = BlockUtils.getTryAndHandlerCrossBlock(mth, handler.handler)
				break
			}
		}
		if (scopeOutBlock != null) {
			// 检查 outBlock 是否仍在 exit 块限制的作用域内
			for (exit in regionMaker.stack.getExits()) {
				if (BlockUtils.isPathExists(exit, scopeOutBlock)) {
					return null
				}
			}
		}
		return scopeOutBlock
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(IfRegionMaker::class.java)

		fun makeIfInfo(mth: MethodNode, ifBlock: BlockNode): IfInfo? {
			val lastInsn = BlockUtils.getLastInsn(ifBlock)
			if (lastInsn == null || lastInsn.type != InsnType.IF) {
				return null
			}
			val ifNode = lastInsn as IfNode
			val condition = IfCondition.fromIfNode(ifNode)
			val info = IfInfo(mth, condition, ifNode.getThenBlock(), ifNode.getElseBlock())
			info.mergedBlocks.add(ifBlock)
			return info
		}

		fun searchNestedIf(info: IfInfo): IfInfo {
			val next = mergeNestedIfNodes(info)
			if (next != null) {
				return next
			}
			return info
		}

		fun findOutBlock(mth: MethodNode, thenBlock: BlockNode?, elseBlock: BlockNode?): BlockNode? {
			if (thenBlock === elseBlock) {
				return thenBlock
			}
			if (thenBlock == null || elseBlock == null) {
				return null
			}

			// Attempt one: 两分支支配边界的交集存在唯一块 —— 直接采用
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

			// Attempt two: 两分支支配边界并集中的候选汇聚块
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
				// 候选可到达路径交叉块、且交叉块不可回达候选时，候选只是中间伪汇聚——
				// 其「两分支可达」经由非分支作用域路径（循环回边/汇聚后路径）成立，
				// 真正的边界是更远的交叉块。取伪汇聚会把 outBlock 提前到分支块自身或
				// 中间块，区域栈边界失效、块被指数级重复处理
				// （CoreTextFieldKt：511 块 → 22.8 万区域节点，反编译超时）。
				val cross = BlockUtils.getPathCross(mth, thenBlock, elseBlock)
				if (cross != null && BlockUtils.isPathExists(bottom, cross) && !BlockUtils.isPathExists(cross, bottom)) {
					return cross
				}
				return bottom
			}

			// Attempt three: 路径交叉
			val cross = BlockUtils.getPathCross(mth, thenBlock, elseBlock)
			if (cross != null) {
				return cross
			}
			return null
		}


		private fun isCandidateForOutBlock(mth: MethodNode, thenBlock: BlockNode, elseBlock: BlockNode, candidate: BlockNode): Boolean {
			if (candidate.predecessors.size < 2) {
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
			// 守卫：候选本身就是某个分支块，且另一分支可经「干净路径」到达一个被两分支同时支配的前驱
			// ——说明该前驱位于真汇聚点下游（分支早已重汇聚，此候选是循环回边回卷出的重入口）。
			// 接受它会把 outBlock 拉回分支块自身，区域栈边界失效、块被反复重复处理
			// （CoreTextFieldKt：511 块 → 22.8 万区域节点，反编译超时）。
			// 仅被支配但无干净路径（纯回边可达）的前驱无害，不拒绝。
			if (candidate === elseBlock || candidate === thenBlock) {
				val otherBlock = if (candidate === elseBlock) thenBlock else elseBlock
				for (pred in candidate.predecessors) {
					if (thenBlock.isDominator(pred) && elseBlock.isDominator(pred) &&
						BlockUtils.isPathExists(otherBlock, pred)
					) {
						return false
					}
				}
			}
			for (pred in candidate.predecessors) {
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

		/** 取「可到达其余所有候选」的最先汇聚点；候选间无全到达关系时返回 null（回退 Attempt three） */
		private fun getFirstMergeBlock(candidates: List<BlockNode>): BlockNode? {
			if (candidates.size <= 1) {
				return candidates.firstOrNull()
			}
			for (top in candidates) {
				var topOk = true
				for (other in candidates) {
					if (other !== top && !BlockUtils.isAnyPathExists(top, other)) {
						topOk = false
						break
					}
				}
				if (topOk) {
					return top
				}
			}
			return null
		}

		private fun isBadBranchBlock(info: IfInfo, block: BlockNode): Boolean {
			// 检查块是否位于循环回边末尾
			if (block.contains(AFlag.LOOP_START) && block.predecessors.size == 1) {
				val pred = block.predecessors[0]
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
			val preds = block.predecessors
			val ifBlocks = info.mergedBlocks
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

		fun mergeNestedIfNodes(currentIf: IfInfo): IfInfo? {
			val curThen0 = currentIf.thenBlock
			val curElse0 = currentIf.elseBlock
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

			val assignInlineNeeded = nextIfVar.forceInlineInsns.isNotEmpty()
			if (assignInlineNeeded) {
				for (mergedBlock in currentIf.mergedBlocks) {
					if (mergedBlock.contains(AFlag.LOOP_START)) {
						// 不要把赋值内联进循环条件
						return currentIf
					}
				}
			}

			if (isInversionNeeded(currentIf, nextIfVar)) {
				nextIfVar = IfInfo.invert(nextIfVar)
			}
			val thenPathSame = BlockUtils.isEqualPaths(curThen, nextIfVar.thenBlock)
			val elsePathSame = BlockUtils.isEqualPaths(curElse, nextIfVar.elseBlock)
			if (!thenPathSame && !elsePathSame) {
				// 复杂条件，做额外检查
				if (checkConditionBranches(curThen, curElse) || checkConditionBranches(curElse, curThen)) {
					return null
				}
				var otherBranchBlock = if (followThenBranch) curElse else curThen
				otherBranchBlock = BlockUtils.followEmptyPath(otherBranchBlock)
				if (!BlockUtils.isPathExists(nextIfVar.mergedBlocks.first, otherBranchBlock)) {
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
			val nextThen0 = getNextIf(currentIf, checkNotNull(currentIf.thenBlock))
			val nextElse0 = getNextIf(currentIf, checkNotNull(currentIf.elseBlock))
			if (nextThen0 == null || nextElse0 == null) {
				return null
			}
			if (checkNotNull(nextThen0.mergedBlocks.first.domFrontier) !=
				checkNotNull(nextElse0.mergedBlocks.first.domFrontier)
			) {
				return null
			}
			var nextThen = searchNestedIf(nextThen0)
			var nextElse = searchNestedIf(nextElse0)
			if (nextThen.thenBlock === nextElse.thenBlock &&
				nextThen.elseBlock === nextElse.elseBlock
			) {
				return mergeTernaryConditions(currentIf, nextThen, nextElse)
			}
			if (nextThen.thenBlock === nextElse.elseBlock &&
				nextThen.elseBlock === nextElse.thenBlock
			) {
				nextElse = IfInfo.invert(nextElse)
				return mergeTernaryConditions(currentIf, nextThen, nextElse)
			}
			return null
		}

		private fun mergeTernaryConditions(currentIf: IfInfo, nextThen: IfInfo, nextElse: IfInfo): IfInfo {
			val newCondition = IfCondition.ternary(
				currentIf.condition,
				nextThen.condition,
				nextElse.condition,
			)
			val result = IfInfo(currentIf.mth, newCondition, nextThen.thenBlock, nextThen.elseBlock)
			result.merge(currentIf, nextThen, nextElse)
			confirmMerge(result)
			return result
		}

		private fun isInversionNeeded(currentIf: IfInfo, nextIf: IfInfo): Boolean = BlockUtils.isEqualPaths(currentIf.elseBlock, nextIf.thenBlock) ||
			BlockUtils.isEqualPaths(currentIf.thenBlock, nextIf.elseBlock)

		private fun canMerge(a: IfInfo, b: IfInfo, followThenBranch: Boolean): Boolean = if (followThenBranch) {
			BlockUtils.isEqualPaths(a.elseBlock, b.elseBlock)
		} else {
			BlockUtils.isEqualPaths(a.thenBlock, b.thenBlock)
		}

		private fun checkConditionBranches(from: BlockNode, to: BlockNode): Boolean {
			val cs = checkNotNull(from.cleanSuccessors)
			return cs.size == 1 && cs.contains(to)
		}

		fun mergeIfInfo(first: IfInfo, second: IfInfo, followThenBranch: Boolean): IfInfo {
			val mth = first.mth
			val skipBlocks = first.skipBlocks
			val thenBlock: BlockNode?
			val elseBlock: BlockNode?
			if (followThenBranch) {
				thenBlock = second.thenBlock
				elseBlock = getBranchBlock(first.elseBlock, second.elseBlock, skipBlocks, mth)
			} else {
				thenBlock = getBranchBlock(first.thenBlock, second.thenBlock, skipBlocks, mth)
				elseBlock = second.elseBlock
			}
			val mergeOperation = if (followThenBranch) IfCondition.Mode.AND else IfCondition.Mode.OR
			val condition = IfCondition.merge(mergeOperation, first.condition, second.condition)
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
			throw KadxRuntimeException("Unexpected merge pattern")
		}

		fun confirmMerge(info: IfInfo) {
			if (info.mergedBlocks.size() > 1) {
				for (block in info.mergedBlocks) {
					if (block !== info.mergedBlocks.first) {
						block.add(AFlag.ADDED_TO_REGION)
					}
				}
			}
			if (info.skipBlocks.isNotEmpty()) {
				for (block in info.skipBlocks) {
					block.add(AFlag.ADDED_TO_REGION)
				}
				info.skipBlocks.clear()
			}
			for (forceInlineInsn in info.forceInlineInsns) {
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
			if (block.predecessors.size == 1) {
				return true
			}
			return info.mergedBlocks.containsAll(block.predecessors)
		}

		private fun getNextIfNodeInfo(info: IfInfo, block: BlockNode?): IfInfo? {
			if (block == null || block.contains(AType.LOOP) || block.contains(AFlag.ADDED_TO_REGION)) {
				return null
			}
			val lastInsn = BlockUtils.getLastInsn(block)
			if (lastInsn != null && lastInsn.type == InsnType.IF) {
				return makeIfInfo(info.mth, block)
			}
			val next = getNextBlockInIfSuccessorChain(block) ?: return null
			if (next.predecessors.size != 1 || next.contains(AFlag.ADDED_TO_REGION)) {
				return null
			}
			val forceInlineInsns = ArrayList<InsnNode>()
			if (!checkInsnsInline(block, next, forceInlineInsns)) {
				return null
			}
			val nextInfo = makeIfInfo(info.mth, next)
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
			val successors = block.successors
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
			if (candidate.instructions.isEmpty()) {
				return getNextBlockInIfSuccessorChain(candidate)
			}
			return candidate
		}

		/** 检查所有指令是否都能内联 */
		private fun checkInsnsInline(block: BlockNode, next: BlockNode, forceInlineInsns: MutableList<InsnNode>): Boolean {
			val insns = block.instructions
			if (insns.isEmpty()) {
				return true
			}
			var pass = true
			for (insn in insns) {
				val res = insn.result ?: return false
				val useList = checkNotNull(res.sVar).useList
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
