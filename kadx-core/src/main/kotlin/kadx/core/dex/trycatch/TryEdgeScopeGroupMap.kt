package kadx.core.dex.trycatch

import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.BlockUtils
import kadx.core.utils.Pair
import kadx.core.utils.exceptions.KadxRuntimeException
import org.jetbrains.annotations.Nullable
import java.util.BitSet
import java.util.LinkedList

/**
 * try 出口边的作用域分组映射。
 *
 * **含义**：键是一条 try 出口边，值是“与该边共享同一逻辑作用域”的其他边 -> 作用域终点块。
 * finally 恢复逻辑用它判断哪些出口边应在同一处合并处理。
 *
 * **Kotlin 转换说明**：
 * - 原 Java `implements Map<TryEdge, Map<TryEdge, BlockNode>>` 改为 Kotlin
 *   `MutableMap<TryEdge, MutableMap<TryEdge, BlockNode?>>`，JVM 擦除完全相同，
 *   Java 调用方无差异；内层值允许为 null（表示两条边没有公共干净后继）。
 * - `getScopeEnds` 的键在找不到“顶部块”时可能为 null（原 Java 允许 null 键），
 *   因此内部使用可空键的 HashMap，返回时按原签名做一次非受检转换。
 */
class TryEdgeScopeGroupMap(
	mth: MethodNode,
	private val tryCatch: TryCatchBlockAttr,
	initialCapacity: Int,
) : MutableMap<TryEdge, MutableMap<TryEdge, BlockNode?>> {

	/** 记录“被合并掉的边”与“保留的边”配对 */
	private val mergedEdges: MutableList<Pair<TryEdge>> = ArrayList()

	private val underlyingMap: HashMap<TryEdge, MutableMap<TryEdge, BlockNode?>> = HashMap(initialCapacity)

	/** 一条边的“边 + 其作用域起点块”组合，供两两配对时使用 */
	private class TryEdgeScope(val edge: TryEdge, val block: BlockNode)

	override val size: Int get() = underlyingMap.size

	override fun isEmpty(): Boolean = underlyingMap.isEmpty()

	override fun containsKey(key: TryEdge): Boolean = underlyingMap.containsKey(key)

	override fun containsValue(value: MutableMap<TryEdge, BlockNode?>): Boolean {
		// 原 Java 逻辑：把 value 当作 TryEdge 去查键（保留原始行为）
		val obj: Any? = value
		if (obj !is TryEdge) {
			return false
		}
		return underlyingMap.containsKey(obj)
	}

	override fun get(key: TryEdge): MutableMap<TryEdge, BlockNode?>? = underlyingMap[key]

	override val entries: MutableSet<MutableMap.MutableEntry<TryEdge, MutableMap<TryEdge, BlockNode?>>>
		get() = underlyingMap.entries

	override val keys: MutableSet<TryEdge> get() = underlyingMap.keys

	override val values: MutableCollection<MutableMap<TryEdge, BlockNode?>> get() = underlyingMap.values

	override fun put(key: TryEdge, value: MutableMap<TryEdge, BlockNode?>): MutableMap<TryEdge, BlockNode?>? = underlyingMap.put(key, value)

	override fun remove(key: TryEdge): MutableMap<TryEdge, BlockNode?>? = underlyingMap.remove(key)

	override fun putAll(from: Map<out TryEdge, MutableMap<TryEdge, BlockNode?>>) {
		underlyingMap.putAll(from)
	}

	override fun clear() {
		underlyingMap.clear()
	}

	fun hasMergedEdges(): Boolean = mergedEdges.isNotEmpty()

	val mergedScopes: List<Pair<TryEdge>> get() = mergedEdges

	/** 先合并同作用域的边，再为每条边建立“边 -> 终点块”的映射 */
	fun populateFromEdges(edges: Map<TryEdge, BlockNode>) {
		mergeSameScopes(edges)

		for (edge in edges.keys) {
			val edgeBlock = checkNotNull(edges[edge])

			val handlerFallthroughMap = createEdgeTerminusMap(edges, edge, edgeBlock)
			put(edge, handlerFallthroughMap)
		}
	}

	/**
	 * 返回所有边的“作用域终点”分组：终点块 -> 在该点结束的边列表。
	 *
	 * 注意：与原 Java 一致，内部允许 null 键（找不到顶部块时），返回时做非受检转换。
	 */
	@Suppress("UNCHECKED_CAST")
	fun getScopeEnds(mth: MethodNode): Map<BlockNode, List<TryEdge>> {
		val groups = HashMap<BlockNode?, MutableList<TryEdge>>()

		// 记录“两条处理器边之间没有公共干净后继”的孤立边
		val isolatedEdgePairs = LinkedList<TryEdge>()

		for (mergeEdgeA in keys) {
			val edgeMergedPair = getMergedNodeFromEdge(mergeEdgeA)
			if (edgeMergedPair != null) {
				continue
			}

			val handlerRelations = checkNotNull(get(mergeEdgeA))

			val scopeEnds = ArrayList<BlockNode>(handlerRelations.size)
			for (mergeEdgeB in handlerRelations.keys) {
				val mergedPairFromRelation = getMergedNodeFromEdge(mergeEdgeB)
				if (mergedPairFromRelation != null && mergedPairFromRelation.first === mergeEdgeA) {
					continue
				}

				val sharedTerminator = handlerRelations[mergeEdgeB]

				if (sharedTerminator == null) {
					// 两条处理器边之间没有公共干净后继
					isolatedEdgePairs.add(mergeEdgeB)
				} else {
					scopeEnds.add(sharedTerminator)
				}
			}

			if (scopeEnds.isEmpty()) {
				continue
			}

			val topGrouping = BlockUtils.getTopBlock(scopeEnds)

			if (groups.containsKey(topGrouping)) {
				checkNotNull(groups[topGrouping]).add(mergeEdgeA)
			} else {
				val groupingHandlers = LinkedList<TryEdge>()
				groupingHandlers.add(mergeEdgeA)
				groups[topGrouping] = groupingHandlers
			}
		}

		for (isolatedEdge in isolatedEdgePairs) {
			var isInList = false
			for (foundEdges in groups.values) {
				if (foundEdges.contains(isolatedEdge)) {
					isInList = true
					break
				}
			}

			if (isInList) {
				// 该孤立边已与其他处理器同组，忽略（与原 Java 一致使用 break）
				break
			}

			// 找到公共后继搜索停止的位置作为该孤立边的作用域终点
			val target = isolatedEdge.target
			val successorBlocks = BlockUtils.collectAllSuccessors(mth, target, true)
			val cleanSuccessorEnd = BlockUtils.getBottomBlock(successorBlocks)
			if (cleanSuccessorEnd == null) {
				throw KadxRuntimeException("Could not find bottom clean successor for isolated try edge")
			}

			val scopeTerminusList: MutableList<TryEdge>
			if (groups.containsKey(cleanSuccessorEnd)) {
				scopeTerminusList = checkNotNull(groups[cleanSuccessorEnd])
			} else {
				scopeTerminusList = LinkedList()
				groups[cleanSuccessorEnd] = scopeTerminusList
			}
			scopeTerminusList.add(isolatedEdge)
		}

		if (groups.size == 1) {
			for (pair in mergedEdges) {
				val keptEdge = pair.first
				val removedEdge = pair.second

				if (keptEdge.isHandlerExit() && !tryCatch.handlers.contains(keptEdge.exceptionHandler)) {
					continue
				}
				if (removedEdge.isHandlerExit() && !tryCatch.handlers.contains(removedEdge.exceptionHandler)) {
					continue
				}

				if (keptEdge.isNotHandlerExit() && removedEdge.isNotHandlerExit()) {
					continue
				}

				for (edgesWithTerminus in groups.values) {
					if (edgesWithTerminus.contains(keptEdge)) {
						edgesWithTerminus.remove(keptEdge)
					}
				}

				val terminus = checkNotNull(get(keptEdge))[removedEdge]
				val terminusEdges: MutableList<TryEdge>
				if (!groups.containsKey(terminus)) {
					terminusEdges = LinkedList()
					terminusEdges.add(keptEdge)
					groups[terminus] = terminusEdges
				} else {
					terminusEdges = checkNotNull(groups[terminus])
				}
				terminusEdges.add(removedEdge)
			}
		}

		return groups as Map<BlockNode, List<TryEdge>>
	}

	@Nullable
	private fun getMergedNodeFromEdge(edge: TryEdge): Pair<TryEdge>? {
		for (pair in mergedEdges) {
			if (pair.second === edge) {
				return pair
			}
		}
		return null
	}

	/**
	 * 为给定边 [edge] 建立它与其他边的“终点块”关系表。
	 *
	 * 终点块通过后支配集合求交 + 沿直接后支配链回溯得到；值可为 null，
	 * 表示两条边没有公共干净后继（调用方据此判定为孤立边）。
	 */
	private fun createEdgeTerminusMap(
		edgeStartMap: Map<TryEdge, BlockNode>,
		edge: TryEdge,
		edgeStart: BlockNode,
	): MutableMap<TryEdge, BlockNode?> {
		val scopeRelations = HashMap<TryEdge, BlockNode?>(edgeStartMap.size - 1)
		for (otherEdge in edgeStartMap.keys) {
			if (edge === otherEdge) {
				continue
			}

			val otherEdgeStart = checkNotNull(edgeStartMap[otherEdge])

			val eitherEdgeIsHandler = edge.isHandlerExit() || otherEdge.isHandlerExit()
			if (otherEdgeStart === edgeStart && eitherEdgeIsHandler) {
				continue
			}

			if (otherEdgeStart.isMthExitBlock) {
				scopeRelations[otherEdge] = otherEdgeStart
				// 一切都汇入出口节点，合并边不再需要
				mergedEdges.clear()
				continue
			}
			if (edgeStart.isMthExitBlock) {
				scopeRelations[otherEdge] = edgeStart
				mergedEdges.clear()
				continue
			}

			val sharedPostDominators = checkNotNull(edgeStart.postDoms).clone() as BitSet
			val otherPostDoms = checkNotNull(otherEdgeStart.postDoms)
			if (sharedPostDominators.isEmpty || otherPostDoms.isEmpty) {
				continue
			}
			sharedPostDominators.and(otherPostDoms)

			val postDomHandler = LinkedList<BlockNode>()
			var currentBlock: BlockNode? = edgeStart
			while (currentBlock != null) {
				postDomHandler.add(currentBlock)
				currentBlock = currentBlock.iPostDom
			}

			var commonPostDom: BlockNode? = null
			currentBlock = otherEdgeStart
			while (currentBlock != null) {
				if (postDomHandler.contains(currentBlock)) {
					commonPostDom = currentBlock
					break
				}
				currentBlock = currentBlock.iPostDom
			}

			scopeRelations[otherEdge] = commonPostDom
		}
		return scopeRelations
	}

	/**
	 * 若两条边的作用域会合并（一条边的终点可达另一条边的起点），记录下来。
	 *
	 * 同时从 [handlers] 中移除被合并掉的边，返回简化后的映射。
	 */
	private fun mergeSameScopes(handlers: Map<TryEdge, BlockNode>): Map<TryEdge, BlockNode> {
		val exceptionHandlers = ArrayList(handlers.entries)

		val handlerPairs = LinkedList<Pair<TryEdgeScope>>()
		for (i in exceptionHandlers.indices) {
			for (j in i + 1 until exceptionHandlers.size) {
				val a = TryEdgeScope(exceptionHandlers[i].key, exceptionHandlers[i].value)
				val b = TryEdgeScope(exceptionHandlers[j].key, exceptionHandlers[j].value)
				handlerPairs.add(Pair(a, b))
			}
		}

		val simplifiedScopes = HashMap(handlers)

		var i = 0
		while (i < handlerPairs.size) {
			val handlerPair = handlerPairs[i]

			val edgeScopeA = handlerPair.first
			val edgeScopeB = handlerPair.second
			val edgeBlockA = edgeScopeA.block
			val edgeBlockB = edgeScopeB.block
			val pathExists = BlockUtils.isPathExists(edgeBlockA, edgeBlockB) ||
				BlockUtils.isPathExists(edgeBlockB, edgeBlockA)
			if (pathExists) {
				val bottomBlock = BlockUtils.getBottomBlock(listOf(edgeBlockA, edgeBlockB))
				// 两块在同一作用域内：从矩阵中移除被合并的一方
				val removeHandler = if (edgeBlockA !== bottomBlock) edgeScopeA.edge else edgeScopeB.edge
				val keepHandler = if (edgeBlockA === bottomBlock) edgeScopeA.edge else edgeScopeB.edge
				simplifiedScopes.remove(removeHandler)
				handlerPairs.removeAt(i)

				mergedEdges.add(Pair(keepHandler, removeHandler))
			} else {
				i++
			}
		}

		return simplifiedScopes
	}
}
