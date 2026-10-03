package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.SameInstructionsStrategy
import jadx.core.dex.visitors.finaly.traverser.GlobalTraverserSourceState
import jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import jadx.core.utils.Pair
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.concurrent.atomic.AtomicReference

/**
 * finally 遍历中的“活动路径状态”。
 *
 * **背景**：为了判断候选分支与 finally 分支是否含有相同的重复指令，遍历器需要同时
 * 跟踪两侧（finally / candidate）各自走到哪里、已匹配了哪些指令对、每个块的匹配进度。
 * 本类就是这条路径的共享上下文，被 [TraverserState] 各状态持有并引用。
 *
 * **Kotlin 转换说明**：
 * - `produceFromFactories` 是 Java 侧大量调用的静态工厂，放入 companion 并加 `@JvmStatic`；
 * - 块/指令一律按引用比较（`===`），因为它们是图节点；
 * - 内部的 [BlockCompletionMonitorMap] 实现 `MutableMap`（JVM 擦除后即 `java.util.Map`），
 *   与 Java 侧无差异。
 */
class TraverserActivePathState {

	/**
	 * 跟踪某个块的指令匹配进度：记录还有哪些指令下标“尚未匹配”。
	 * 全部移除后说明整个块都已匹配（[isEntireBlock]）。
	 */
	private class BlockCompletionMonitor(val block: BlockNode) {
		private val matchedIndices: MutableSet<Int>

		init {
			val insnCount = block.getInstructions().size
			matchedIndices = HashSet(insnCount)
			for (i in 0 until insnCount) {
				matchedIndices.add(i)
			}
		}

		fun registerWithBlockInfo(info: TraverserBlockInfo, numberMatched: Int) {
			if (info.block !== block) {
				return
			}
			val botPointer = info.bottomOffset
			for (i in 0 until numberMatched) {
				val indexMatched = botPointer + i
				matchedIndices.remove(indexMatched)
			}
			val bottomImplicitCount = info.bottomImplicitCount
			val noPathEndInsns = botPointer - bottomImplicitCount == 0
			if (noPathEndInsns) {
				for (i in 0 until bottomImplicitCount) {
					matchedIndices.remove(i)
				}
			}
		}

		fun duplicate(): BlockCompletionMonitor {
			val dup = BlockCompletionMonitor(block)
			dup.matchedIndices.retainAll(matchedIndices)
			return dup
		}

		fun mergeWith(other: BlockCompletionMonitor) {
			if (other.block !== block) {
				return
			}
			matchedIndices.retainAll(other.matchedIndices)
		}

		fun isEntireBlock(): Boolean = matchedIndices.isEmpty()
	}

	/**
	 * [BlockCompletionMonitor] 的映射封装。
	 *
	 * 原 Java 直接 `implements Map<BlockNode, BlockCompletionMonitor>`，Kotlin 侧改用
	 * `MutableMap`（字节码完全相同）；`containsValue` 保留原 Java 的“把值当键查”的怪异行为。
	 */
	private class BlockCompletionMonitorMap : MutableMap<BlockNode, BlockCompletionMonitor> {

		private val underlying: MutableMap<BlockNode, BlockCompletionMonitor> = HashMap()

		override val size: Int get() = underlying.size

		override fun isEmpty(): Boolean = underlying.isEmpty()

		override fun containsKey(key: BlockNode): Boolean = underlying.containsKey(key)

		override fun containsValue(value: BlockCompletionMonitor): Boolean {
			// 原 Java 逻辑：把 value 当作 BlockNode 去查键（保留原始行为）
			val obj: Any? = value
			if (obj !is BlockNode) {
				return false
			}
			return underlying.containsKey(obj)
		}

		override fun get(key: BlockNode): BlockCompletionMonitor? = underlying[key]

		override val entries: MutableSet<MutableMap.MutableEntry<BlockNode, BlockCompletionMonitor>>
			get() = underlying.entries

		override val keys: MutableSet<BlockNode> get() = underlying.keys

		override val values: MutableCollection<BlockCompletionMonitor> get() = underlying.values

		override fun put(key: BlockNode, value: BlockCompletionMonitor): BlockCompletionMonitor? = underlying.put(key, value)

		override fun remove(key: BlockNode): BlockCompletionMonitor? = underlying.remove(key)

		override fun putAll(from: Map<out BlockNode, BlockCompletionMonitor>) {
			underlying.putAll(from)
		}

		override fun clear() {
			underlying.clear()
		}

		fun registerWithBlockInfo(info: TraverserBlockInfo, numberMatched: Int) {
			val block = info.block
			if (containsKey(block)) {
				checkNotNull(get(block)).registerWithBlockInfo(info, numberMatched)
			} else {
				val monitor = BlockCompletionMonitor(block)
				monitor.registerWithBlockInfo(info, numberMatched)
				put(block, monitor)
			}
		}

		fun mergeEntry(other: BlockCompletionMonitor) {
			val block = other.block
			if (containsKey(block)) {
				checkNotNull(get(block)).mergeWith(other)
			} else {
				val monitor = other.duplicate()
				put(block, monitor)
			}
		}

		fun mergeMap(other: BlockCompletionMonitorMap) {
			for (monitor in other.values) {
				mergeEntry(monitor)
			}
		}

		fun duplicate(): BlockCompletionMonitorMap {
			val dup = BlockCompletionMonitorMap()
			for (sourceBlock in keys) {
				val monitor = checkNotNull(get(sourceBlock))
				dup.put(sourceBlock, monitor.duplicate())
			}
			return dup
		}
	}

	val finallyStateRef: AtomicReference<TraverserState>
	val candidateStateRef: AtomicReference<TraverserState>
	val finallyGlobalState: GlobalTraverserSourceState
	val candidateGlobalState: GlobalTraverserSourceState
	private val commonGlobalState: TraverserGlobalCommonState

	val matchedInsns: MutableSet<Pair<InsnNode>>
	private val finallyCompletionMonitor: BlockCompletionMonitorMap
	private val candidateCompletionMonitor: BlockCompletionMonitorMap

	/**
	 * 创建一条全新的遍历路径，供遍历器控制器开始一次新的搜索。
	 *
	 * 两侧（finally / candidate）各自根据终止块是否为空决定是否允许跳过首块，
	 * 并分别建立“新块”状态与全局包含块集合。
	 */
	constructor(
		mth: MethodNode,
		sameInstructionsStrategy: SameInstructionsStrategy,
		finallyBlockTerminus: BlockNode,
		candidateBlockTerminus: BlockNode,
		finallyBlocks: List<BlockNode>,
		candidateBlocks: List<BlockNode>,
	) {
		val shouldFinallyAllowFirstBlockSkip = finallyBlockTerminus.getInstructions().isNotEmpty()
		val shouldCandidateAllowFirstBlockSkip = candidateBlockTerminus.getInstructions().isNotEmpty()
		val finallyCentralityState = CentralityState(sameInstructionsStrategy, shouldFinallyAllowFirstBlockSkip)
		val candidateCentralityState = CentralityState(sameInstructionsStrategy, shouldCandidateAllowFirstBlockSkip)

		val finallyBlockInfo = TraverserBlockInfo(finallyBlockTerminus)
		val candidateBlockInfo = TraverserBlockInfo(candidateBlockTerminus)

		val finallyState: TraverserState = NewBlockTraverserState(this, finallyCentralityState, finallyBlockInfo)
		val candidateState: TraverserState = NewBlockTraverserState(this, candidateCentralityState, candidateBlockInfo)

		this.finallyGlobalState = GlobalTraverserSourceState(HashSet(finallyBlocks))
		this.candidateGlobalState = GlobalTraverserSourceState(HashSet(candidateBlocks))
		this.commonGlobalState = TraverserGlobalCommonState(mth)

		this.finallyStateRef = AtomicReference(finallyState)
		this.candidateStateRef = AtomicReference(candidateState)
		this.matchedInsns = HashSet()
		this.finallyCompletionMonitor = BlockCompletionMonitorMap()
		this.candidateCompletionMonitor = BlockCompletionMonitorMap()
	}

	/**
	 * 复制用的私有构造器：由 [produceFromFactories] 调用，
	 * 先创建空的状态引用，再回填两侧的克隆状态。
	 */
	private constructor(
		matchedInsns: MutableSet<Pair<InsnNode>>,
		finallyCompletionMonitor: BlockCompletionMonitorMap,
		candidateCompletionMonitor: BlockCompletionMonitorMap,
		commonGlobalState: TraverserGlobalCommonState,
		finallyGlobalState: GlobalTraverserSourceState,
		candidateGlobalState: GlobalTraverserSourceState,
	) {
		this.finallyStateRef = AtomicReference()
		this.candidateStateRef = AtomicReference()
		this.matchedInsns = matchedInsns
		this.finallyGlobalState = finallyGlobalState
		this.candidateGlobalState = candidateGlobalState
		this.commonGlobalState = commonGlobalState
		this.finallyCompletionMonitor = finallyCompletionMonitor
		this.candidateCompletionMonitor = candidateCompletionMonitor
	}

	/**
	 * 深拷贝整条路径：指令对集合与两侧匹配进度都复制一份，避免分支间相互影响；
	 * 全局状态（含块集合、公共缓存）则按引用共享。
	 */
	fun duplicate(): TraverserActivePathState {
		val dMatchedInsns = HashSet(matchedInsns)
		val dFinallyCompletionMonitor = finallyCompletionMonitor.duplicate()
		val dCandidateCompletionMonitor = candidateCompletionMonitor.duplicate()
		val dState = TraverserActivePathState(
			dMatchedInsns,
			dFinallyCompletionMonitor,
			dCandidateCompletionMonitor,
			commonGlobalState,
			finallyGlobalState,
			candidateGlobalState,
		)

		val dFinallyState = getFinallyState().duplicate(dState)
		val dCandidateState = getCandidateState().duplicate(dState)
		dState.candidateStateRef.set(dCandidateState)
		dState.finallyStateRef.set(dFinallyState)
		return dState
	}

	fun getFinallyState(): TraverserState = finallyStateRef.get()

	fun getCandidateState(): TraverserState = candidateStateRef.get()

	/** 返回持有 [state] 的原子引用；若不是本路径的状态则返回 null。 */
	fun getReferenceForState(state: TraverserState): AtomicReference<TraverserState>? {
		if (finallyStateRef.get() === state) {
			return finallyStateRef
		}
		if (candidateStateRef.get() === state) {
			return candidateStateRef
		}
		return null
	}

	/** 返回 [state] 所属子图的全局源块状态（finally 或 candidate）。 */
	fun getGlobalStateFor(state: TraverserState): GlobalTraverserSourceState {
		if (finallyStateRef.get() === state) {
			return finallyGlobalState
		}
		if (candidateStateRef.get() === state) {
			return candidateGlobalState
		}
		throw JadxRuntimeException("Orphaned TraverserState node")
	}

	val globalCommonState: TraverserGlobalCommonState get() = commonGlobalState

	/** 把另一批路径的匹配结果合并进来（指令对取并集，块进度取交集）。 */
	fun mergeWith(otherStates: List<TraverserActivePathState>) {
		for (otherState in otherStates) {
			matchedInsns.addAll(otherState.matchedInsns)

			finallyCompletionMonitor.mergeMap(otherState.finallyCompletionMonitor)
			candidateCompletionMonitor.mergeMap(otherState.candidateCompletionMonitor)
		}
	}

	/** 根据块属于 finally 还是 candidate 子图，登记到对应的匹配进度表。 */
	fun registerWithBlockInfo(info: TraverserBlockInfo, numberMatched: Int) {
		val block = info.block
		val isFinallyBlock = finallyGlobalState.isBlockContained(block)
		val monitorMap = if (isFinallyBlock) finallyCompletionMonitor else candidateCompletionMonitor
		monitorMap.registerWithBlockInfo(info, numberMatched)
	}

	val allFullyMatchedFinallyBlocks: MutableSet<BlockNode> get() = getAllFullyMatchedBlocks(finallyCompletionMonitor)

	val allFullyMatchedCandidateBlocks: MutableSet<BlockNode> get() = getAllFullyMatchedBlocks(candidateCompletionMonitor)

	private fun getAllFullyMatchedBlocks(monitorMap: BlockCompletionMonitorMap): MutableSet<BlockNode> {
		val matches = HashSet<BlockNode>()
		for (monitor in monitorMap.values) {
			if (!monitor.isEntireBlock()) {
				continue
			}
			matches.add(monitor.block)
		}
		return matches
	}

	companion object {
		/**
		 * 基于已有的活动路径状态，用给定的工厂生成两侧的新状态。
		 *
		 * 注意：这里**不会**深拷贝指令对集合与匹配进度表（与原 Java 一致），
		 * 而是与 [previousTraverserState] 共享，只有在需要分叉时调用方才会先 duplicate。
		 */
		fun produceFromFactories(
			previousTraverserState: TraverserActivePathState,
			finallyStateProducer: TraverserStateFactory<*>,
			candidateStateProducer: TraverserStateFactory<*>,
		): TraverserActivePathState {
			val dState = TraverserActivePathState(
				previousTraverserState.matchedInsns,
				previousTraverserState.finallyCompletionMonitor,
				previousTraverserState.candidateCompletionMonitor,
				previousTraverserState.commonGlobalState,
				previousTraverserState.finallyGlobalState,
				previousTraverserState.candidateGlobalState,
			)

			val dFinallyState = finallyStateProducer.generateState(dState)
			val dCandidateState = candidateStateProducer.generateState(dState)
			dState.candidateStateRef.set(dCandidateState)
			dState.finallyStateRef.set(dFinallyState)
			return dState
		}
	}
}
