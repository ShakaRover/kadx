package jadx.core.dex.visitors.ssa

import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.BitSet

/**
 * 活跃变量分析（Live Variable Analysis），用于 SSA 构造时确定 PHI 指令的位置。
 *
 * **做什么**：对每个基本块计算“入口处仍活跃的寄存器集合”（liveIn）。
 * 某寄存器在某点“活跃”是指：从该点出发存在一条路径，在它被重新赋值之前会读取该寄存器。
 *
 * **为什么**：SSA 构造的经典算法（Cytron 等）需要“赋值块集合 + 支配边界 + 活跃性”
 * 来决定在哪些块插入 PHI 指令。本类提供 [getAssignBlocks] 与 [isLive] 两个查询。
 *
 * **数据**：
 * - [uses]（gen）：块内“先读后写”的寄存器；
 * - [defs]（kill）：块内被赋值的寄存器；
 * - [assignBlocks]：`寄存器 -> 被赋值的块集合`（按位图存储）；
 * - [liveIn]：`块 -> 入口活跃寄存器集合`。
 *
 * **Kotlin 转换说明**：字段在 [runAnalysis] 中初始化，因此用 `lateinit`；
 * 迭代使用普通的 `do/while`，与原 Java 完全一致。
 */
class LiveVarAnalysis(private val mth: MethodNode) {

	private lateinit var uses: Array<BitSet>
	private lateinit var defs: Array<BitSet>
	private lateinit var liveIn: Array<BitSet>
	private lateinit var assignBlocks: Array<BitSet>

	fun runAnalysis() {
		val bbCount = checkNotNull(mth.basicBlocks).size
		val regsCount = mth.getRegsCount()
		this.uses = initBitSetArray(bbCount, regsCount)
		this.defs = initBitSetArray(bbCount, regsCount)
		this.assignBlocks = initBitSetArray(regsCount, bbCount)
		fillBasicBlockInfo()
		processLiveInfo()
	}

	/** 返回“该寄存器在哪些块中被赋值”的位图。 */
	fun getAssignBlocks(regNum: Int): BitSet = assignBlocks[regNum]

	fun isLive(blockId: Int, regNum: Int): Boolean {
		if (blockId >= liveIn.size) {
			LOG.warn("LiveVarAnalysis: out of bounds block: {}, max: {}", blockId, liveIn.size)
			return false
		}
		return liveIn[blockId].get(regNum)
	}

	fun isLive(block: BlockNode, regNum: Int): Boolean = isLive(block.pos, regNum)

	/**
	 * 统计每个块内的 use/def 信息。
	 *
	 * 对块内指令顺序扫描：读取某寄存器时，若它尚未在本块被赋值，则记入 [uses]；
	 * 遇到结果寄存器时记入 [defs]，并把该块加入该寄存器的赋值块集合。
	 */
	private fun fillBasicBlockInfo() {
		for (block in checkNotNull(mth.basicBlocks)) {
			val blockId = block.pos
			val gen = uses[blockId]
			val kill = defs[blockId]
			for (insn in block.instructions) {
				for (arg in insn.getArguments()) {
					if (arg.isRegister) {
						val regNum = (arg as RegisterArg).regNum
						if (!kill.get(regNum)) {
							gen.set(regNum)
						}
					}
				}
				val result = insn.result
				if (result != null) {
					val regNum = result.regNum
					kill.set(regNum)
					assignBlocks[regNum].set(blockId)
				}
			}
		}
	}

	/**
	 * 迭代求解 liveIn，直到不再变化。
	 *
	 * 数据流方程：`liveIn[b] = (∪ liveIn[successor]) - defs[b] ∪ uses[b]`。
	 * 为避免不收敛，设置迭代上限 `块数 * 10`。
	 */
	private fun processLiveInfo() {
		val bbCount = checkNotNull(mth.basicBlocks).size
		val regsCount = mth.getRegsCount()
		val liveInBlocks = initBitSetArray(bbCount, regsCount)
		val blocks = checkNotNull(mth.basicBlocks)
		val blocksCount = blocks.size
		val iterationsLimit = blocksCount * 10
		var changed: Boolean
		var k = 0
		do {
			changed = false
			for (block in blocks) {
				val blockId = block.pos
				val prevIn = liveInBlocks[blockId]
				val newIn = BitSet(regsCount)
				for (successor in block.successors) {
					newIn.or(liveInBlocks[successor.pos])
				}
				newIn.andNot(defs[blockId])
				newIn.or(uses[blockId])
				if (prevIn != newIn) {
					changed = true
					liveInBlocks[blockId] = newIn
				}
			}
			if (k++ > iterationsLimit) {
				throw JadxRuntimeException("Live variable analysis reach iterations limit, blocks count: $blocksCount")
			}
		} while (changed)
		this.liveIn = liveInBlocks
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(LiveVarAnalysis::class.java)

		private fun initBitSetArray(length: Int, bitsCount: Int): Array<BitSet> = Array(length) { BitSet(bitsCount) }
	}
}
