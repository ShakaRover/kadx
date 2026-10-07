package kadx.core.dex.visitors.finaly

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.trycatch.ExceptionHandler
import kadx.core.dex.trycatch.TryCatchBlockAttr
import kadx.core.dex.trycatch.TryEdge
import kadx.core.dex.trycatch.TryEdgeScopeGroupMap
import kadx.core.utils.BlockUtils
import kadx.core.utils.ListUtils

/**
 * try/catch 出口边 -> 该边在 try/catch 作用域内可达的块列表。
 *
 * **用途**：finally 恢复需要知道每条 try 出口边能“看到”哪些块，用来判断重复指令的作用域。
 *
 * **Kotlin 转换说明**：原 Java `implements Map<TryEdge, List<BlockNode>>` 改为
 * `MutableMap<TryEdge, MutableList<BlockNode>>`（JVM 擦除相同）；`anyBlockHasNonImplicitTry`
 * 与 `getAllInScope` 为 Java 侧静态调用，放入 companion 并加 `@JvmStatic`。
 */
class TryCatchEdgeBlockMap : MutableMap<TryEdge, MutableList<BlockNode>> {

	// 必须是 LinkedHashMap（不能改回 HashMap）：`keys`/`values` 会被 MarkFinallyVisitor 按顺序
	// 遍历来决定 finally 提取路径。TryEdge 的 hashCode 基于 BlockNode 身份哈希，
	// HashMap 的迭代顺序在每个 JVM 进程都不同，会造成输出非确定性。
	private val underlying: MutableMap<TryEdge, MutableList<BlockNode>> = LinkedHashMap()

	override val size: Int get() = underlying.size

	override fun isEmpty(): Boolean = underlying.isEmpty()

	override fun containsKey(key: TryEdge): Boolean = underlying.containsKey(key)

	override fun containsValue(value: MutableList<BlockNode>): Boolean {
		// 原 Java 逻辑：把 value 当作 TryEdge 去查键（保留原始行为）
		val obj: Any? = value
		if (obj !is TryEdge) {
			return false
		}
		return underlying.containsKey(obj)
	}

	override fun get(key: TryEdge): MutableList<BlockNode>? = underlying[key]

	override val entries: MutableSet<MutableMap.MutableEntry<TryEdge, MutableList<BlockNode>>>
		get() = underlying.entries

	override val keys: MutableSet<TryEdge> get() = underlying.keys

	override val values: MutableCollection<MutableList<BlockNode>> get() = underlying.values

	override fun put(key: TryEdge, value: MutableList<BlockNode>): MutableList<BlockNode>? = underlying.put(key, value)

	override fun remove(key: TryEdge): MutableList<BlockNode>? = underlying.remove(key)

	override fun putAll(from: Map<out TryEdge, MutableList<BlockNode>>) {
		underlying.putAll(from)
	}

	override fun clear() {
		underlying.clear()
	}

	/** 找出 finally 处理器对应的边所关联的块列表。 */
	fun getBlocksForHandler(handler: ExceptionHandler): MutableList<BlockNode>? {
		var edgeWithHandler: TryEdge? = null
		for (edge in keys) {
			if (edge.isNotHandlerExit()) {
				continue
			}
			if (edge.exceptionHandler != handler) {
				continue
			}
			edgeWithHandler = edge
			break
		}
		if (edgeWithHandler == null) {
			return null
		}
		return get(edgeWithHandler)
	}

	/** 汇总所有“非处理器出口”（贯穿/提前退出等）边关联的块。 */
	val blocksForAllFallthroughs: MutableList<BlockNode> get() {
		val blks = ArrayList<BlockNode>()
		for (edge in keys) {
			if (edge.isHandlerExit()) {
				continue
			}
			blks.addAll(checkNotNull(get(edge)))
		}
		return blks
	}

	companion object {
		/** 判断给定块集合中是否存在“非隐式”的 try 块（隐式 try 在反编译中不应保留）。 */
		fun anyBlockHasNonImplicitTry(blocks: List<BlockNode>): Boolean {
			val blocksWithTries = ListUtils.filter(blocks) { it.contains(AFlag.EXC_TOP_SPLITTER) }
			if (blocksWithTries.isEmpty()) {
				return false
			}
			for (topSplitter in blocksWithTries) {
				var block: TryCatchBlockAttr? = null
				for (topSplitterSuccessor in checkNotNull(topSplitter.cleanSuccessors)) {
					if (topSplitterSuccessor.contains(AType.TRY_BLOCK)) {
						block = topSplitterSuccessor.get(AType.TRY_BLOCK)
					}
				}
				if (block == null) {
					continue
				}
				if (!TryCatchBlockAttr.isImplicitOrMerged(block)) {
					return true
				}
			}
			return false
		}

		/**
		 * 计算整个 try/catch 作用域内每条出口边可达的块。
		 *
		 * 若某条边可达的块里含有其它 try，则需要连同非 clean 后继一起收集；
		 * 若是“贯穿”出口，还要把 try 体本身算进去；最后从结果中剔除 finally 块。
		 */
		fun getAllInScope(
			mth: MethodNode,
			tryCatch: TryCatchBlockAttr,
			scopeGroups: TryEdgeScopeGroupMap,
			finallyHandler: ExceptionHandler,
			scopeTerminusGroups: Map<BlockNode, List<TryEdge>>,
		): TryCatchEdgeBlockMap {
			val edgeBlocks = tryCatch.edgeBlockMap
			val result = TryCatchEdgeBlockMap()
			for (scopeTerminus in scopeTerminusGroups.keys) {
				val sourceEdges = checkNotNull(scopeTerminusGroups[scopeTerminus])
				for (sourceEdge in sourceEdges) {
					val edgeBlock = checkNotNull(edgeBlocks[sourceEdge])
					val useClean = !(
						sourceEdge.isNotHandlerExit() &&
							ListUtils.anyMatch(scopeGroups.mergedScopes) { pair -> pair.second.isNotHandlerExit() }
						)
					var allBlocks = asMutableList(
						BlockUtils.collectAllSuccessorsUntil(mth, edgeBlock, useClean) { block -> block === scopeTerminus },
					)
					val anyBlockHasTry = anyBlockHasNonImplicitTry(allBlocks)
					if (anyBlockHasTry && useClean) {
						// 找到的块里还有 try 出口，则收集所有后继，而不仅是 clean 后继。
						allBlocks = asMutableList(
							BlockUtils.collectAllSuccessorsUntil(mth, edgeBlock, false) { block -> block === scopeTerminus },
						)
					}
					if (sourceEdge.isNotHandlerExit()) {
						// 贯穿出口：把 try 体也加入作用域。
						allBlocks = ArrayList(allBlocks)
						allBlocks.addAll(tryCatch.getBlocks())
					}
					result.put(sourceEdge, allBlocks)
				}
			}

			val finallyBlocks = result.getBlocksForHandler(finallyHandler)
			if (finallyBlocks != null) {
				for (edge in result.keys) {
					if (edge.isHandlerExit() && edge.exceptionHandler === finallyHandler) {
						continue
					}
					checkNotNull(result[edge]).removeAll(finallyBlocks)
				}
			}
			return result
		}

		/** 把 [BlockUtils] 返回的只读 `List` 视图转成可变列表（底层实际都是 ArrayList）。 */
		@Suppress("UNCHECKED_CAST")
		private fun <T> asMutableList(list: List<T>): MutableList<T> = list as MutableList<T>
	}
}
