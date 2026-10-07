package kadx.core.dex.visitors.finaly

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.trycatch.ExceptionHandler
import kadx.core.dex.trycatch.TryCatchBlockAttr
import kadx.core.dex.trycatch.TryEdge
import kadx.core.dex.trycatch.TryEdgeScopeGroupMap
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.ConstInlineVisitor
import kadx.core.dex.visitors.KadxVisitor
import kadx.core.dex.visitors.finaly.traverser.TraverserController
import kadx.core.dex.visitors.finaly.traverser.TraverserException
import kadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import kadx.core.dex.visitors.ssa.SSATransform
import kadx.core.utils.BlockUtils
import kadx.core.utils.ListUtils
import kadx.core.utils.Pair
import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.LinkedList

/**
 * 从各分支末尾“重复出现”的指令中提取 finally 块。
 *
 * **算法总览**：
 * 1. 对每个 try 块找出 catch-all 处理器，并计算各出口边的“作用域”（哪些块属于同一条路径），
 *    结果记录在 [TryEdgeScopeGroupMap] 中；
 * 2. 用 [TraverserController] 从每个“作用域终点”沿作用域内的块反向遍历，
 *    与“候选 finally”块逐一比较控制流与指令；若匹配，则把指令标记为重复指令；
 * 3. 最后把识别出的重复指令与 finally 指令分别打上 [AFlag]（`FINALLY_INSNS` / `DONT_GENERATE`），
 *    交给后续 region 处理。
 *
 * **Kotlin 转换说明**：
 * - 所有私有静态辅助方法移入 `companion object`（仅内部调用，无需 `@JvmStatic`）；
 * - 块/处理器引用比较一律用 `===`；指令值比较用 `==`；
 * - 原 Java 会通过只读视图直接修改 `TryCatchBlockAttr` 的内部列表，
 *   这里用 `asMutableList` 显式转回可变列表以保留同样语义。
 */
@KadxVisitor(
	name = "MarkFinallyVisitor",
	desc = "Search and mark duplicate code generated for finally block",
	runAfter = [SSATransform::class],
	runBefore = [ConstInlineVisitor::class],
)
class MarkFinallyVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode() || mth.isNoExceptionHandlers()) {
			return
		}
		try {
			var implicitHandlerRemoved = false
			val tryBlocks = mth.getAll(AType.TRY_BLOCKS_LIST)
			val processRequiredTryBlocks = ArrayList<TryCatchBlockAttr>()

			// 遍历所有异常处理器：
			// - 移除隐式处理器；
			// - 把非隐式处理器标记为需要搜索 finally。
			for (tryBlock in tryBlocks) {
				val tryInfo = getTryBlockData(mth, tryBlock) ?: continue
				val cutHandlerBlocks = cutHandlerBlocks(mth, tryInfo, tryInfo.finallyHandler) ?: continue
				if (attemptRemoveImplicitHandlers(cutHandlerBlocks, tryInfo)) {
					implicitHandlerRemoved = true
				} else {
					processRequiredTryBlocks.add(tryBlock)
				}
			}
			// 若发现隐式处理器，需要把它们移除。
			if (implicitHandlerRemoved) {
				resetTryBlocks(mth, tryBlocks)
			}

			// 遍历所有非隐式处理器并搜索 finally。
			var finallyExtracted = false
			for (tryBlock in processRequiredTryBlocks) {
				// 由于隐式处理器已被移除，这里需要重新计算作用域分组。
				val tryInfo = getTryBlockData(mth, tryBlock) ?: continue
				cutHandlerBlocks(mth, tryInfo, tryInfo.finallyHandler)
				finallyExtracted = finallyExtracted or processTryBlock(mth, tryInfo)
			}
			// 若有处理器被合并，重新整理 try 块列表。
			if (finallyExtracted) {
				resetTryBlocks(mth, tryBlocks)
			}
		} catch (e: Exception) {
			LOG.error("MarkFinallyVisitor error", e)
			undoFinallyVisitor(mth)
			mth.addWarnComment("Undo finally extract visitor", e)
		}
	}

	/** 单个 try 块的提取上下文：作用域、处理器、出口终点分组与可达块。 */
	private class TryExtractInfo(
		val tryBlock: TryCatchBlockAttr,
		val scopeGroups: TryEdgeScopeGroupMap,
		val finallyHandler: ExceptionHandler,
		fallthroughGroups: Map<BlockNode, List<TryEdge>>,
		val handlerScopes: TryCatchEdgeBlockMap,
	) {
		val scopeTerminusGroups: Map<BlockNode, List<TryEdge>> = fallthroughGroups
		val rethrowBlocks: MutableSet<BlockNode> = HashSet()
		var completeFinallyBlocks: MutableSet<BlockNode>? = null
		var completeCandidateBlocks: MutableSet<BlockNode>? = null
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(MarkFinallyVisitor::class.java)

		private fun resetTryBlocks(mth: MethodNode, tryBlocks: List<TryCatchBlockAttr>) {
			mth.clearExceptionHandlers()
			// 从方法属性里移除已合并或空的 try 块。
			val clearedTryBlocks = ArrayList(tryBlocks)
			if (clearedTryBlocks.removeAll { TryCatchBlockAttr.isImplicitOrMerged(it) }) {
				mth.remove(AType.TRY_BLOCKS_LIST)
				mth.addAttr(AType.TRY_BLOCKS_LIST, clearedTryBlocks)
			}
		}

		/**
		 * 计算一个 try 块的作用域信息：各出口边的处理器块、相对作用域、逻辑包含块。
		 *
		 * @return 被识别为 catch-all 的处理器对应的提取上下文；找不到则返回 null。
		 */
		private fun getTryBlockData(mth: MethodNode, tryBlock: TryCatchBlockAttr): TryExtractInfo? {
			if (tryBlock.isMerged()) {
				return null
			}
			// 找出 catch-all 处理器。
			var allHandler: ExceptionHandler? = null
			for (excHandler in tryBlock.handlers) {
				if (excHandler.isCatchAll()) {
					allHandler = excHandler
					break
				}
			}
			if (allHandler == null) {
				return null
			}
			val scopeGroups = tryBlock.getExecutionScopeGroups(mth)
			val fallthroughGroups = tryBlock.getHandlerFallthroughGroups(mth, scopeGroups)
			val handlerScopes = TryCatchEdgeBlockMap.getAllInScope(mth, tryBlock, scopeGroups, allHandler, fallthroughGroups)
			return TryExtractInfo(tryBlock, scopeGroups, allHandler, fallthroughGroups, handlerScopes)
		}

		/**
		 * 处理单个 try 块，尝试在各分支间找到公共指令以提取 finally。
		 *
		 * @return 是否成功提取出 finally 块。
		 */
		private fun processTryBlock(mth: MethodNode, tryInfo: TryExtractInfo): Boolean {
			if (tryInfo.rethrowBlocks.isEmpty()) {
				return false
			}
			if (extractFinally(mth, tryInfo)) {
				for (rethrowBlock in tryInfo.rethrowBlocks) {
					val lastInsn = BlockUtils.getLastInsn(rethrowBlock) ?: continue
					lastInsn.add(AFlag.DONT_GENERATE)
				}
				return true
			}
			return false
		}

		/**
		 * 裁剪处理器块：去掉 `move-exception` 起始块与“重新抛出”路径出口，
		 * 剩余的块才是真正需要参与 finally 比较的逻辑。
		 */
		private fun cutHandlerBlocks(
			mth: MethodNode,
			tryInfo: TryExtractInfo,
			handler: ExceptionHandler,
		): MutableList<BlockNode>? {
			val handlerBlock = handler.getHandlerBlock()
			val handlerBlocks = tryInfo.handlerScopes.getBlocksForHandler(handler) ?: return null

			val handlerFinalInsn = BlockUtils.getFirstInsn(handlerBlock)
			if (handlerFinalInsn != null && handlerFinalInsn.type == InsnType.MOVE_EXCEPTION) {
				if (handlerBlock != null) {
					handlerBlocks.remove(handlerBlock) // 排除带有 'move-exception' 的块
				}
			}

			val bottomBlock = BlockUtils.getBottomBlock(handlerBlocks)
			if (bottomBlock == null) {
				mth.addWarn("Bottom block not found for handler: " + handler)
				return handlerBlocks
			}
			val pathExits = BlockUtils.followEmptyUpPathWithinSet(bottomBlock, handlerBlocks)
			if (pathExits.isEmpty()) {
				return handlerBlocks
			}
			for (pathExit in pathExits) {
				// 要能提取 finally，必须保证所有进入处理器逻辑的路径都以 THROW 结束，
				// 且抛出的是 move-exception 的结果。
				val bottomBlockLastInsn = BlockUtils.getLastInsn(pathExit)
				val isValidPathExit = bottomBlockLastInsn != null &&
					handlerFinalInsn != null &&
					bottomBlockLastInsn.type == InsnType.THROW &&
					bottomBlockLastInsn.argsCount > 0 &&
					bottomBlockLastInsn.getArg(0) == handlerFinalInsn.result
				if (!isValidPathExit) {
					return handlerBlocks
				}
			}
			val cutHandlerBlocks = ArrayList(handlerBlocks)
			for (pathExit in pathExits) {
				cutHandlerBlocks.remove(pathExit)
				removeEmptyUpPath(cutHandlerBlocks, pathExit)
				tryInfo.rethrowBlocks.add(pathExit)
			}
			return cutHandlerBlocks
		}

		/**
		 * 尝试识别并移除隐式 try/catch。
		 *
		 * @return 该 try 块是否为隐式并已被移除。
		 */
		private fun attemptRemoveImplicitHandlers(cutHandlerBlocks: List<BlockNode>, tryInfo: TryExtractInfo): Boolean {
			if (!(cutHandlerBlocks.isEmpty() || BlockUtils.isAllBlocksEmpty(cutHandlerBlocks))) {
				return false
			}
			// 移除空的 catch。
			checkNotNull(tryInfo.finallyHandler.getTryBlock()).removeHandler(tryInfo.finallyHandler)
			return true
		}

		/** 搜索并标记 try 体与各处理器之间公共的重复代码。 */
		private fun extractFinally(mth: MethodNode, tryInfo: TryExtractInfo): Boolean {
			// 收集当前 try 及其内部 try 的所有处理器。
			val hasInnerBlocks = tryInfo.tryBlock.getInnerTryBlocks().isNotEmpty()
			val handlers = getHandlersForTryCatch(tryInfo.tryBlock)
			if (handlers.isEmpty()) {
				return false
			}
			val insns = findCommonInsns(mth, tryInfo)
			if (insns == null || insns.isEmpty()) {
				return false
			}
			val ignoredFinallyInsns = HashSet<InsnNode>()
			val ignoredCandidateInsns = HashSet<InsnNode>()
			// 必须是 LinkedHashMap（不能改回 HashMap）：下面按此顺序遍历并**原地修改**
			// （打 AFlag、copyCodeVars），且循环体内可能 `return false` 放弃整块 finally 提取。
			// InsnNode 使用身份 hashCode，HashMap 的迭代顺序在每个 JVM 进程都不同 ——
			// 曾导致同一输入两次反编译分别产出 `finally{}` 与 `catch(Throwable){ ...; throw th; }`。
			val insnMap = LinkedHashMap<InsnNode, MutableList<InsnNode>>()
			for ((finallyInsn, candidateInsns) in insns) {
				// 一条指令要被认定为重复，出现的次数必须等于“除 finally 处理器外的边数”。
				if (candidateInsns.size != tryInfo.handlerScopes.size - 1) {
					ignoredFinallyInsns.add(finallyInsn)
					ignoredCandidateInsns.addAll(candidateInsns)
					// TODO: 支持部分 `catch (Throwable)` finally 子句。
					return false
				}
				insnMap[finallyInsn] = candidateInsns
			}
			for ((finallyInsn, candidateInsns) in insnMap) {
				finallyInsn.add(AFlag.FINALLY_INSNS)
				for (candidateInsn in candidateInsns) {
					copyCodeVars(finallyInsn, candidateInsn)
					candidateInsn.add(AFlag.DONT_GENERATE)
				}
			}
			for (finallyBlock in checkNotNull(tryInfo.completeFinallyBlocks)) {
				if (ListUtils.anyMatch(finallyBlock.instructions) { ignoredFinallyInsns.contains(it) }) {
					// 若该块含有未在所有 try 边中找到的指令，则不把它标记为 finally 块。
					continue
				}
				finallyBlock.add(AFlag.FINALLY_INSNS)
			}
			for (candidateBlock in checkNotNull(tryInfo.completeCandidateBlocks)) {
				if (ListUtils.anyMatch(candidateBlock.instructions) { ignoredCandidateInsns.contains(it) }) {
					// 若该块含有“未在所有 try 边中找到”的重复指令，则不标记为重复块。
					continue
				}
				candidateBlock.add(AFlag.DONT_GENERATE)
			}

			// 若某个作用域已与 try 的贯穿分支合并，则不要合并内部 try；否则合并。
			val mergedFallthroughScope =
				ListUtils.anyMatch(tryInfo.scopeGroups.mergedScopes) { scopePair -> scopePair.first.isNotHandlerExit() }
			val mergeInnerTryBlocks = hasInnerBlocks && !mergedFallthroughScope

			tryInfo.finallyHandler.setFinally(true)
			if (mergeInnerTryBlocks) {
				val innerTryBlocks = tryInfo.tryBlock.getInnerTryBlocks()
				for (innerTryBlock in innerTryBlocks) {
					asMutableList(tryInfo.tryBlock.handlers).addAll(innerTryBlock.handlers)
					asMutableList(tryInfo.tryBlock.getBlocks()).addAll(innerTryBlock.getBlocks())
					innerTryBlock.setMerged(true)
				}
				tryInfo.tryBlock.setBlocks(ArrayList(ListUtils.distinctList(tryInfo.tryBlock.getBlocks())))
				asMutableList(innerTryBlocks).clear()
			}
			return true
		}

		/**
		 * 获取该 try 块（含其内部 try）的全部异常处理器。
		 */
		private fun getHandlersForTryCatch(tryBlock: TryCatchBlockAttr): List<ExceptionHandler> {
			val hasInnerBlocks = tryBlock.getInnerTryBlocks().isNotEmpty()
			if (hasInnerBlocks) {
				val handlers = ArrayList(tryBlock.handlers)
				for (innerTryBlock in tryBlock.getInnerTryBlocks()) {
					handlers.addAll(getHandlersForTryCatch(innerTryBlock))
				}
				return handlers
			}
			return tryBlock.handlers
		}

		/** 把只读 `List` 视图转回可变列表（原 Java 直接修改内部列表）。 */
		@Suppress("UNCHECKED_CAST")
		private fun <T> asMutableList(list: List<T>): MutableList<T> = list as MutableList<T>

		/**
		 * 从每个 try 出口边出发，反向遍历其作用域并与 finally 候选块比较，
		 * 收集匹配到的指令对以及“整块匹配”的 finally / 候选块。
		 */
		private fun findCommonInsns(mth: MethodNode, tryInfo: TryExtractInfo): Map<InsnNode, MutableList<InsnNode>>? {
			val allHandlerBlocks = checkNotNull(tryInfo.handlerScopes.getBlocksForHandler(tryInfo.finallyHandler))
			val finallyScopeTerminus = getTerminusForHandler(tryInfo.finallyHandler, tryInfo) ?: return null
			// 同上：插入顺序决定 findCommonInsns 的遍历顺序，进而影响 finally 提取决策。
			val matchingInsns = LinkedHashMap<InsnNode, MutableList<InsnNode>>()
			for (edge in tryInfo.handlerScopes.keys) {
				if (edge.isHandlerExit() && edge.exceptionHandler === tryInfo.finallyHandler) {
					continue
				}
				val handlerBlocks = checkNotNull(tryInfo.handlerScopes[edge])
				var scopeTerminus: BlockNode? = null
				for (edgeTerminusBlock in tryInfo.scopeTerminusGroups.keys) {
					val edgesWithTerminus = checkNotNull(tryInfo.scopeTerminusGroups[edgeTerminusBlock])
					if (edgesWithTerminus.contains(edge)) {
						scopeTerminus = edgeTerminusBlock
						break
					}
				}
				if (scopeTerminus == null) {
					throw KadxRuntimeException("Expected to find fallthrough terminus for handler " + edge)
				}
				val comparatorState = TraverserActivePathState(
					mth,
					SameInstructionsStrategyImpl(),
					finallyScopeTerminus,
					scopeTerminus,
					allHandlerBlocks,
					handlerBlocks,
				)
				val controller = TraverserController()
				val pathResults: List<TraverserActivePathState>
				try {
					pathResults = controller.process(comparatorState)
				} catch (e: TraverserException) {
					LOG.error("Could not search for finally duplicate instructions in path", e)
					return null
				}
				val completeFinally = HashSet<BlockNode>()
				val completeCandidate = HashSet<BlockNode>()
				for (pathResult in pathResults) {
					for (matchingInsnPair in pathResult.matchedInsns) {
						val finallyInsn = matchingInsnPair.first
						val candidateInsn = matchingInsnPair.second
						val candidateInsnsList = matchingInsns.getOrPut(finallyInsn) { LinkedList() }
						candidateInsnsList.add(candidateInsn)
					}
					completeFinally.addAll(pathResult.allFullyMatchedFinallyBlocks)
					completeCandidate.addAll(pathResult.allFullyMatchedCandidateBlocks)
				}
				if (tryInfo.completeFinallyBlocks == null) {
					tryInfo.completeFinallyBlocks = completeFinally
				} else {
					checkNotNull(tryInfo.completeFinallyBlocks).retainAll(completeFinally)
				}
				if (tryInfo.completeCandidateBlocks == null) {
					tryInfo.completeCandidateBlocks = completeCandidate
				} else {
					checkNotNull(tryInfo.completeCandidateBlocks).addAll(completeCandidate)
				}
			}
			return matchingInsns
		}

		/** 递归移除从 [startBlock] 往上的空前驱块。 */
		private fun removeEmptyUpPath(handlerBlocks: MutableList<BlockNode>, startBlock: BlockNode) {
			for (pred in startBlock.predecessors) {
				if (pred.isEmpty()) {
					if (handlerBlocks.remove(pred) && !BlockUtils.isBackEdge(pred, startBlock)) {
						removeEmptyUpPath(handlerBlocks, pred)
					}
				}
			}
		}

		/** 把 [fromInsn] 的代码变量信息复制到 [toInsn]（结果与各参数）。 */
		private fun copyCodeVars(fromInsn: InsnNode, toInsn: InsnNode) {
			copyCodeVars(fromInsn.result, toInsn.result)
			val argsCount = fromInsn.argsCount
			for (i in 0 until argsCount) {
				copyCodeVars(fromInsn.getArg(i), toInsn.getArg(i))
			}
		}

		private fun copyCodeVars(fromArg: InsnArg?, toArg: InsnArg?) {
			if (fromArg == null || toArg == null ||
				!fromArg.isRegister || !toArg.isRegister
			) {
				return
			}
			val fromSsaVar = (fromArg as RegisterArg).sVar
			val toSsaVar = (toArg as RegisterArg).sVar
			checkNotNull(toSsaVar).setCodeVar(checkNotNull(fromSsaVar).codeVar)
		}

		/** 不应用本 visitor 重新加载方法（提取失败时的回退）。 */
		private fun undoFinallyVisitor(mth: MethodNode) {
			try {
				mth.root().getProcessClasses().processMethodUntilVisitor(mth, "MarkFinallyVisitor", false)
			} catch (e: Exception) {
				mth.addError("Undo finally extract failed", e)
			}
		}

		/** 找出 finally 处理器对应的作用域终点块。 */
		private fun getTerminusForHandler(handler: ExceptionHandler, tryInfo: TryExtractInfo): BlockNode? {
			for (terminus in tryInfo.scopeTerminusGroups.keys) {
				val edgesWithTerminus = checkNotNull(tryInfo.scopeTerminusGroups[terminus])
				for (edge in edgesWithTerminus) {
					if (edge.isNotHandlerExit()) {
						continue
					}
					if (edge.exceptionHandler == handler) {
						return terminus
					}
				}
			}
			return null
		}
	}
}
