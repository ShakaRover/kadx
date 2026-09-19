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
import jadx.core.utils.EmptyBitSet.EMPTY as EMPTY_BITSET

class BlockNode(
	val cid: Int,
	var pos: Int,
	val startOffset: Int,
) : AttrNode(),
	IBlock,
	Comparable<BlockNode> {
	val instructions = ArrayList<InsnNode>(2)
	var predecessors = ArrayList<BlockNode>(1)
	var successors = ArrayList<BlockNode>(1)
	private var cleanSuccessors: List<BlockNode>? = null

	/** All dominators, excluding self */
	var doms: BitSet = EMPTY_BITSET

	/** Post dominators, excluding self */
	var postDoms: BitSet = EMPTY_BITSET

	/** Dominance frontier */
	var domFrontier: BitSet? = null

	/** Immediate dominator */
	var idom: BlockNode? = null

	/** Immediate post dominator */
	var iPostDom: BlockNode? = null

	private var dominatesOn = ArrayList<BlockNode>(3)

	override fun getInstructions(): List<InsnNode> = instructions

	fun getPredecessors(): List<BlockNode> = predecessors

	fun getSuccessors(): List<BlockNode> = successors

	fun getCleanSuccessors(): List<BlockNode>? = cleanSuccessors

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
			successors = lockList(successors) as ArrayList<BlockNode>
			cleanSuccessors = if (cleanSuccessors === successors) this.successors else lockList(cleanSuccessors!!) as List<BlockNode>
			predecessors = lockList(predecessors) as ArrayList<BlockNode>
			dominatesOn = lockList(dominatesOn) as ArrayList<BlockNode>
			if (domFrontier == null) {
				throw JadxRuntimeException("Dominance frontier not set for block: $this")
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to lock block: $this", e)
		}
	}

	fun isDominator(block: BlockNode): Boolean = doms.get(block.pos)

	fun getDominatesOn(): List<BlockNode> = dominatesOn

	fun addDominatesOn(block: BlockNode) {
		dominatesOn.add(block)
	}

	fun isSynthetic(): Boolean = contains(AFlag.SYNTHETIC)

	fun isReturnBlock(): Boolean = contains(AFlag.RETURN)

	fun isMthExitBlock(): Boolean = contains(AFlag.MTH_EXIT_BLOCK)

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
