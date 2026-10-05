package kadx.core.dex.trycatch

import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.BlockUtils
import kadx.core.utils.ListUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.Collections
import java.util.LinkedList

/**
 * 一个 try 块的完整描述（对应源码里的一个 `try { ... } catch/finally`）。
 *
 * **职责**：
 * - 保存该 try 覆盖的基本块集合 [blocks] 与所有异常处理器 [handlers]；
 * - 记录与外层 try（[outerTryBlock]）、内层 try（[innerTryBlocks]）的嵌套关系；
 * - 为 finally 恢复逻辑提供“出口边”（[getTryEdges]）及其作用域分组。
 *
 * **相等性**：按 `id + handlers + blocks` 判等（原 Java 手写实现），保持普通 class。
 *
 * **Kotlin 转换说明**：
 * - 所有 getter/setter 保持显式函数（大量 Kotlin 调用方以 `tb.getHandlers()` 形式调用）；
 * - Java 的 `==` 引用比较（如 try 块身份比较）改写为 `===`；
 * - 静态方法 [isImplicitOrMerged] 放入 companion 并标注 `@JvmStatic`。
 */
class TryCatchBlockAttr(
	val id: Int,
	val handlers: MutableList<ExceptionHandler>,
	private var blocks: MutableList<BlockNode>,
) : IKadxAttribute {

	private var outerTryBlock: TryCatchBlockAttr? = null
	private var innerTryBlocks: MutableList<TryCatchBlockAttr> = Collections.emptyList()
	private var merged: Boolean = false

	private var topSplitter: BlockNode? = null

	init {
		// 建立处理器 -> try 块的反向引用
		handlers.forEach { it.setTryBlock(this) }
	}

	/** 是否只有一个“捕获全部”（Throwable）处理器 */
	fun isAllHandler(): Boolean = handlers.size == 1 && handlers[0].isCatchAll()

	/**
	 * 判断该 try 体是否“只做抛出”：每个块恰好一条指令，且至少出现一次 THROW。
	 *
	 * 允许的指令只有 MOVE_EXCEPTION / MONITOR_EXIT / THROW（其余一律判定为非“只抛出”）。
	 */
	fun isThrowOnly(): Boolean {
		var throwFound = false
		for (block in blocks) {
			val insns = block.instructions
			if (insns.size != 1) {
				return false
			}
			val insn = insns[0]
			when (insn.type) {
				InsnType.MOVE_EXCEPTION,
				InsnType.MONITOR_EXIT,
				-> {
					// 允许的指令
				}

				InsnType.THROW -> throwFound = true

				else -> return false
			}
		}
		return throwFound
	}

	val handlersCount: Int get() = handlers.size

	fun getBlocks(): List<BlockNode> = blocks

	fun setBlocks(blocks: MutableList<BlockNode>) {
		this.blocks = blocks
	}

	/** 清空 try 块：移除所有块与处理器，并标记处理器待删除 */
	fun clear() {
		blocks.clear()
		handlers.forEach { it.markForRemove() }
		handlers.clear()
	}

	fun removeBlock(block: BlockNode) {
		blocks.remove(block)
	}

	fun removeHandler(handler: ExceptionHandler) {
		handlers.remove(handler)
		handler.markForRemove()
	}

	fun getInnerTryBlocks(): List<TryCatchBlockAttr> = innerTryBlocks

	fun addInnerTryBlock(inner: TryCatchBlockAttr) {
		if (innerTryBlocks.isEmpty()) {
			innerTryBlocks = ArrayList()
		}
		innerTryBlocks.add(inner)
	}

	fun getOuterTryBlock(): TryCatchBlockAttr? = outerTryBlock

	fun setOuterTryBlock(outerTryBlock: TryCatchBlockAttr?) {
		this.outerTryBlock = outerTryBlock
	}

	fun getTopSplitter(): BlockNode? = topSplitter

	fun setTopSplitter(topSplitter: BlockNode?) {
		this.topSplitter = topSplitter
	}

	fun isMerged(): Boolean = merged

	fun setMerged(merged: Boolean) {
		this.merged = merged
	}

	fun id(): Int = id

	/**
	 * 计算所有“处理器出口边”：从 try 体底部/顶部拆分块到各处理器入口块。
	 *
	 * 找不到底部拆分块时，用 try 体所有块与处理器前驱的交集来推断；
	 * 仍找不到则回退到顶部拆分块。
	 */
	val handlerTryEdges: List<TryEdge> get() {
		val mergedHandlers = getMergedHandlers()
		val edges = ArrayList<TryEdge>(mergedHandlers.size)
		for (handler in mergedHandlers) {
			val handlerBlock = checkNotNull(handler.getHandlerBlock())
			var handlerSplitter = handler.bottomSplitter
			if (handlerSplitter == null) {
				// 无法找到底部拆分块时，用处理器前驱中属于 try 体的块推断
				val allChildren = ListUtils.filter(handlerBlock.predecessors) { getBlocks().contains(it) }
				handlerSplitter = BlockUtils.getBottomBlock(allChildren)
				if (handlerSplitter == null) {
					handlerSplitter = getTopSplitter()
				}
			}
			val edge = TryEdge(checkNotNull(handlerSplitter), handlerBlock, handler)
			edges.add(edge)
		}
		return edges
	}

	val fallthroughTryEdges: List<TryEdge> get() {
		val edges = LinkedList<TryEdge>()
		val exploredBlocks = ArrayList<BlockNode>()
		val exploredTrys = LinkedList<TryCatchBlockAttr>()

		getFallthroughTryEdges(edges, exploredBlocks, exploredTrys)
		return edges
	}

	fun getFallthroughTryEdges(
		edges: MutableList<TryEdge>,
		exploredBlocks: MutableList<BlockNode>,
		exploredTrys: MutableList<TryCatchBlockAttr>,
	) {
		val mergedHandlers = getMergedHandlers()
		val searchBlocks = HashSet(getBlocks())
		for (handler in mergedHandlers) {
			handler.blocks.forEach { searchBlocks.remove(it) }
		}
		val sourceBlock = BlockUtils.getTopBlock(ArrayList(searchBlocks))
		if (sourceBlock != null) {
			exploredTrys.add(this)
			exploreTryPath(edges, sourceBlock, searchBlocks, exploredBlocks, exploredTrys)
		}
	}

	val tryEdges: List<TryEdge> get() {
		val handlerEdges = handlerTryEdges
		val fallthroughEdges = fallthroughTryEdges
		val edges = ArrayList<TryEdge>(handlerEdges.size + fallthroughEdges.size)
		edges.addAll(handlerEdges)
		edges.addAll(fallthroughEdges)
		return Collections.unmodifiableList(edges)
	}

	private fun exploreTryPath(
		edges: MutableList<TryEdge>,
		blk: BlockNode,
		searchBlocks: Set<BlockNode>,
		exploredBlocks: MutableList<BlockNode>,
		exploredTrys: MutableList<TryCatchBlockAttr>,
	) {
		for (successor in blk.successors) {
			// 已探索过的分支无需重复计算
			if (exploredBlocks.contains(successor)) {
				continue
			}
			// 底部拆分块不是出口，忽略
			if (successor.contains(AFlag.EXC_BOTTOM_SPLITTER)) {
				continue
			}

			exploredBlocks.add(successor)

			if (successor.contains(AFlag.LOOP_END)) {
				val loops = checkNotNull(successor.get(AType.LOOP)).list
				val loopStartBlocks = LinkedList<BlockNode>()
				for (loop in loops) {
					loopStartBlocks.add(loop.start)
					val loopEdges = loop.exitEdges
					for (loopEdge in loopEdges) {
						if (loopEdge.target === successor) {
							loopStartBlocks.add(loopEdge.source)
						}
					}
				}
				val includesAllLoopStart = ListUtils.allMatch(loopStartBlocks) { exploredBlocks.contains(it) }
				if (!includesAllLoopStart) {
					edges.add(TryEdge(blk, successor, TryEdgeType.LOOP_EXIT))
					continue
				}
			}

			var isPathToAnySearchBlock = false
			for (searchBlock in searchBlocks) {
				if (BlockUtils.isPathExists(successor, searchBlock)) {
					isPathToAnySearchBlock = true
					break
				}
			}
			if (!searchBlocks.contains(successor) && !isPathToAnySearchBlock) {
				// 该块不在本 try 的块集合内：要么是出口，要么通向出口（如异常处理器）
				val allBlocksWithCurrent = ArrayList<BlockNode>(getBlocks().size + 1)
				allBlocksWithCurrent.addAll(getBlocks())
				allBlocksWithCurrent.add(successor)
				val bottomBlock = BlockUtils.getBottomBlock(allBlocksWithCurrent)

				if (bottomBlock != null && bottomBlock !== successor) {
					// 该块通向出口，继续探索
					exploreTryPath(edges, successor, searchBlocks, exploredBlocks, exploredTrys)
					continue
				}

				val emptyPathEndOfSuccessor = BlockUtils.followEmptyPath(successor, false, false)

				if (emptyPathEndOfSuccessor.contains(AFlag.EXC_TOP_SPLITTER)) {
					// 该出口进入同一作用域内的另一个 try：把那个 try 的出口边并入本 try
					val nestedTrys = HashSet<TryCatchBlockAttr>()
					val allSuccessorsOnTryBody = ListUtils.filter(emptyPathEndOfSuccessor.successors) {
						it.contains(AFlag.TRY_ENTER)
					}
					for (tryBodyEnter in allSuccessorsOnTryBody) {
						val nestedTry = tryBodyEnter.get(AType.TRY_BLOCK)
						if (nestedTry == null) {
							continue
						}
						// 已处理过的 try 跳过，避免无限递归
						if (exploredTrys.contains(nestedTry)) {
							continue
						}
						// 顶部拆分块必须相同才视为同一作用域内的嵌套 try
						if (nestedTry.getTopSplitter() !== getTopSplitter()) {
							continue
						}
						nestedTrys.add(nestedTry)
					}

					if (nestedTrys.isNotEmpty()) {
						for (nestedTry in nestedTrys) {
							nestedTry.getFallthroughTryEdges(edges, exploredBlocks, exploredTrys)
						}
						continue
					}
				}

				if (bottomBlock == null) {
					// 在所有 try 块逻辑执行完之前就退出
					edges.add(TryEdge(blk, successor, TryEdgeType.PREMATURE_EXIT))
				} else if (bottomBlock === successor) {
					// 所有 try 块逻辑执行完之后才退出
					edges.add(TryEdge(blk, successor, TryEdgeType.TRUE_FALLTHROUGH))
				} else {
					throw KadxRuntimeException(
						"Unexpected code execution branch taken during try edge resolution: blk=" +
							blk + ",successor=" + successor,
					)
				}
			} else {
				exploreTryPath(edges, successor, searchBlocks, exploredBlocks, exploredTrys)
			}
		}
	}

	/**
	 * 收集本 try 块及所有内层 try 块的处理器（去重前的合并列表）。
	 *
	 * 注意：与原 Java 一致，这里只合并一层内层 try，不做递归。
	 */
	fun getMergedHandlers(): List<ExceptionHandler> {
		val hasInnerBlocks = getInnerTryBlocks().isNotEmpty()
		val mergedHandlers: List<ExceptionHandler>
		if (hasInnerBlocks) {
			val list = ArrayList(handlers)
			for (innerTryBlock in getInnerTryBlocks()) {
				list.addAll(innerTryBlock.handlers)
			}
			mergedHandlers = list
		} else {
			mergedHandlers = handlers
		}
		return Collections.unmodifiableList(mergedHandlers)
	}

	/** 出口边 -> 该边的目标块 */
	val edgeBlockMap: Map<TryEdge, BlockNode> get() {
		val edges = tryEdges
		val blockMap = HashMap<TryEdge, BlockNode>()
		for (edge in edges) {
			blockMap[edge] = edge.target
		}
		return blockMap
	}

	/** 构建出口边的作用域分组映射 */
	fun getExecutionScopeGroups(mth: MethodNode): TryEdgeScopeGroupMap {
		val handlerBlocks = edgeBlockMap
		val scopeGroups = TryEdgeScopeGroupMap(mth, this, handlerBlocks.size)
		scopeGroups.populateFromEdges(handlerBlocks)

		return scopeGroups
	}

	fun getHandlerFallthroughGroups(mth: MethodNode, scopeGroups: TryEdgeScopeGroupMap): Map<BlockNode, List<TryEdge>> = scopeGroups.getScopeEnds(mth)

	/**
	 * 从贯穿分组中找出 finally 体的搜索块：即那些“与某个处理器作用域起点连通”的作用域终点前驱。
	 */
	fun getSearchBlocksFromFallthroughGroups(
		mth: MethodNode,
		finallyHandler: ExceptionHandler,
		fallthroughGroups: Map<BlockNode, List<TryEdge>>,
	): List<BlockNode> {
		val searchBlocks = LinkedList<BlockNode>()
		for ((scopeEndBlock, sourceHandlers) in fallthroughGroups) {
			for (scopeEndPredecessor in scopeEndBlock.predecessors) {
				// 筛选出“非 finally 处理器出口”且目标可到达该前驱的边
				val matchedHandlerPaths = sourceHandlers
					.filter { handler -> !(handler.isHandlerExit() && handler.exceptionHandler === finallyHandler) }
					.map { handler -> handler.target }
					.filter { scopeStart -> BlockUtils.isPathExists(scopeStart, scopeEndPredecessor) }
				if (matchedHandlerPaths.isNotEmpty()) {
					searchBlocks.add(scopeEndPredecessor)
				}
			}
		}
		return searchBlocks
	}

	override val attrType: IKadxAttrType<*> get() = AType.TRY_BLOCK

	override fun hashCode(): Int = handlers.hashCode() + 31 * blocks.hashCode()

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val that = other as TryCatchBlockAttr
		return id == that.id &&
			handlers == that.handlers &&
			blocks == that.blocks
	}

	override fun toString(): String {
		if (merged) {
			return "Merged into $outerTryBlock"
		}
		val sb = StringBuilder()
		sb.append("TryCatch #").append(id).append(" {").append(Utils.listToString(handlers))
		sb.append(", blocks: (").append(Utils.listToString(blocks)).append(')')
		if (topSplitter != null) {
			sb.append(", top: ").append(topSplitter)
		}
		if (outerTryBlock != null) {
			sb.append(", outer: #").append(checkNotNull(outerTryBlock).id)
		}
		if (innerTryBlocks.isNotEmpty()) {
			sb.append(", inners: ").append(Utils.listToString(innerTryBlocks) { "#" + it.id })
		}
		sb.append(" }")
		return sb.toString()
	}

	companion object {
		/** 判断 try 块是否“隐式或已合并”（已合并到外层，或没有任何处理器） */
		fun isImplicitOrMerged(tryBlock: TryCatchBlockAttr): Boolean = tryBlock.isMerged() || tryBlock.handlers.isEmpty()
	}
}
