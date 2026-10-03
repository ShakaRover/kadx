package jadx.core.dex.nodes

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.AttrNode
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.utils.BlockUtils.isExceptionHandlerPath
import jadx.core.utils.InsnUtils.formatOffset
import jadx.core.utils.Utils.lockList
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.BitSet
import jadx.core.utils.EmptyBitSet.Companion.EMPTY as EMPTY_BITSET

class BlockNode(
	// 常量 ID（cid 的 Java getter 原名为 getCId，这里显式指定 JVM 名以保持 Java 调用方兼容）
	@get:JvmName("getCId")
	val cid: Int,
	var pos: Int,
	val startOffset: Int,
) : AttrNode(),
	IBlock,
	Comparable<BlockNode> {
	// instructions 覆盖 IBlock.instructions：属性 getter 直接生成 getInstructions()，
	// 返回 MutableList（JVM 上擦除为 java.util.List）以便原地增删指令
	override val instructions: MutableList<InsnNode> = ArrayList(2)

	var predecessors: List<BlockNode> = ArrayList(1)

	var successors: List<BlockNode> = ArrayList(1)
	var cleanSuccessors: List<BlockNode>? = null
		private set

	/** 所有支配节点（不含自身）。原 Java 允许为 null（BlockProcessor.clearBlocksState 会置空），故保留可空 */
	var doms: BitSet? = EMPTY_BITSET

	/** 后支配节点（不含自身）。原 Java 允许为 null（PostDominatorTree 会判空），故保留可空 */
	var postDoms: BitSet? = EMPTY_BITSET

	/** Dominance frontier */
	var domFrontier: BitSet? = null

	/** 直接支配节点（Immediate dominator）。Java 侧通过 getIDom/setIDom 访问，故显式指定 JVM 名 */
	@get:JvmName("getIDom")
	@set:JvmName("setIDom")
	var idom: BlockNode? = null

	/** Immediate post dominator */
	var iPostDom: BlockNode? = null

	private var dominatesOnList: List<BlockNode> = ArrayList(3)

	val dominatesOn: List<BlockNode> get() = dominatesOnList

	fun updateCleanSuccessors() {
		cleanSuccessors = cleanSuccessors(this)
	}

	companion object {
		fun updateBlockPositions(blocks: List<BlockNode>) {
			for (i in blocks.indices) {
				blocks[i].pos = i
			}
		}

		private fun cleanSuccessors(block: BlockNode): List<BlockNode> {
			val sucList = block.successors
			if (sucList.isEmpty()) {
				return sucList
			}
			val toRemove = ArrayList<BlockNode>(sucList.size)
			for (b in sucList) {
				if (isExceptionHandlerPath(b)) {
					toRemove.add(b)
				}
			}
			if (block.contains(AFlag.LOOP_END)) {
				val loops = block.getAll(AType.LOOP)
				for (loop in loops) {
					toRemove.add(loop.start)
				}
			}
			if (toRemove.isEmpty()) {
				return sucList
			}
			val result = ArrayList<BlockNode>(sucList)
			result.removeAll(toRemove)
			return result
		}
	}

	fun lock() {
		try {
			// 先保存旧的 successors 引用：原 Java 用它与 cleanSuccessors 做身份比较，
			// 若相同则直接复用锁定后的 successors，否则单独锁定 cleanSuccessors。
			val successorsList = successors
			successors = lockList(successorsList)
			cleanSuccessors = if (successorsList === cleanSuccessors) successors else lockList(checkNotNull(cleanSuccessors))
			predecessors = lockList(predecessors)
			dominatesOnList = lockList(dominatesOnList)
			if (domFrontier == null) {
				throw JadxRuntimeException("Dominance frontier not set for block: $this")
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to lock block: $this", e)
		}
	}

	fun isDominator(block: BlockNode): Boolean = checkNotNull(doms).get(block.pos)

	/**
	 * 已废弃：请使用 [getPos]。保留此方法仅为兼容旧 Java 调用方。
	 */
	@Deprecated("Use getPos()")
	val id: Int get() = pos

	@Suppress("UNCHECKED_CAST")
	fun addDominatesOn(block: BlockNode) {
		(dominatesOnList as MutableList<BlockNode>).add(block)
	}

	val isSynthetic: Boolean get() = contains(AFlag.SYNTHETIC)

	val isReturnBlock: Boolean get() = contains(AFlag.RETURN)

	val isMthExitBlock: Boolean get() = contains(AFlag.MTH_EXIT_BLOCK)

	fun isEmpty(): Boolean = instructions.isEmpty()

	override fun hashCode(): Int = cid

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is BlockNode) return false
		return cid == other.cid
	}

	override fun compareTo(o: BlockNode): Int = cid.compareTo(o.cid)

	override fun baseString(): String = cid.toString()

	override fun toString(): String = "B:$cid:${formatOffset(startOffset)}"
}
