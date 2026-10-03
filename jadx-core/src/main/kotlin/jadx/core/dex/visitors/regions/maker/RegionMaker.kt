package jadx.core.dex.visitors.regions.maker

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.EdgeInsnAttr
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.SwitchInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnContainer
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.Region
import jadx.core.utils.BlockUtils
import jadx.core.utils.blocks.BlockSet
import jadx.core.utils.exceptions.JadxOverflowException

/**
 * 从 CFG 入口块出发，按深度优先顺序构建区域树。
 *
 * **算法意图**：`makeRegion` 从某个起点块开始，沿控制流前进：
 * - 遇到循环头 → 交给 [LoopRegionMaker]；
 * - 遇到 if → 交给 [IfRegionMaker]；
 * - 遇到 switch → 交给 [SwitchRegionMaker]；
 * - 遇到 monitor-enter → 交给 [SynchronizedRegionMaker]；
 * - 否则把块顺序加入当前区域，继续走向后继。
 * 通过 [RegionStack] 记录“退出边界”，保证子区域只在自己的范围内推进。
 *
 * Kotlin 转换说明：`getStack/makeRegion` 等原包级可见方法声明为 `internal`，
 * 供同模块的 maker 类调用；对象引用比较用 `===`。
 */
class RegionMaker(mth: MethodNode) {
	private val mth: MethodNode = mth
	val stack: RegionStack = RegionStack(mth)

	private val ifMaker: IfRegionMaker = IfRegionMaker(mth, this)
	private val loopMaker: LoopRegionMaker = LoopRegionMaker(mth, this, ifMaker)

	private val processedBlocks: BlockSet = BlockSet.empty(mth)
	private val regionsLimit: Int = checkNotNull(mth.basicBlocks).size * 400

	private var regionsCount = 0

	fun makeMthRegion(): Region = makeRegion(checkNotNull(mth.enterBlock))

	fun makeRegion(startBlock: BlockNode): Region {
		val region = Region(stack.peekRegion())
		if (stack.containsExit(startBlock)) {
			insertEdgeInsns(region, startBlock)
			return region
		}
		if (processedBlocks.addChecked(startBlock)) {
			// 同一个块被加入多个区域（反编译代码会重复），继续处理但给出告警
			if (!startBlock.contains(AFlag.DUPLICATED)) {
				mth.addWarnComment("Code duplicated, block: " + startBlock + ' ' + startBlock.getAttributesString())
				startBlock.add(AFlag.DUPLICATED)
			}
		}
		var next: BlockNode? = startBlock
		while (next != null) {
			next = traverse(region, next)
			regionsCount++
			if (regionsCount > regionsLimit) {
				throw JadxOverflowException("Regions count limit reached at block " + startBlock)
			}
		}
		return region
	}

	/**
	 * 从 [block] 递归前进，直到遇到退出边界块为止。
	 *
	 * @return 下一个应继续处理的块；null 表示本区域结束
	 */
	private fun traverse(r: Region, block: BlockNode): BlockNode? {
		if (block.contains(AFlag.MTH_EXIT_BLOCK)) {
			return null
		}
		var next: BlockNode? = null
		var processed = false

		val loops = block.getAll(AType.LOOP)
		val loopCount = loops.size
		if (loopCount != 0 && block.contains(AFlag.LOOP_START)) {
			if (loopCount == 1) {
				next = loopMaker.process(r, loops[0], stack)
				processed = true
			} else {
				for (loop in loops) {
					if (loop.start === block) {
						next = loopMaker.process(r, loop, stack)
						processed = true
						break
					}
				}
			}
		}

		val insn = BlockUtils.getLastInsn(block)
		if (!processed && insn != null) {
			when (insn.type) {
				InsnType.IF -> {
					next = ifMaker.process(r, block, insn as IfNode, stack)
					processed = true
				}

				InsnType.SWITCH -> {
					val switchMaker = SwitchRegionMaker(mth, this)
					next = switchMaker.process(r, block, insn as SwitchInsn, stack)
					processed = true
				}

				InsnType.MONITOR_ENTER -> {
					val syncMaker = SynchronizedRegionMaker(mth, this)
					next = syncMaker.process(r, block, insn, stack)
					processed = true
				}

				else -> {}
			}
		}
		if (!processed) {
			r.add(block)
			next = BlockUtils.getNextBlock(block)
		}
		if (next != null && !stack.containsExit(block) && !stack.containsExit(next)) {
			return next
		}
		return null
	}

	private fun insertEdgeInsns(region: Region, exitBlock: BlockNode) {
		val edgeInsns = exitBlock.getAll(AType.EDGE_INSN)
		if (edgeInsns.isEmpty()) {
			return
		}
		val insns = ArrayList<InsnNode>(edgeInsns.size)
		addOneInsnOfType(insns, edgeInsns, InsnType.BREAK)
		addOneInsnOfType(insns, edgeInsns, InsnType.CONTINUE)
		region.add(InsnContainer(insns))
	}

	private fun addOneInsnOfType(insns: MutableList<InsnNode>, edgeInsns: List<EdgeInsnAttr>, insnType: InsnType) {
		for (edgeInsn in edgeInsns) {
			val insn = edgeInsn.insn
			if (insn.type == insnType) {
				insns.add(insn)
				return
			}
		}
	}

	fun isProcessed(block: BlockNode): Boolean = processedBlocks.contains(block)

	fun clearBlockProcessedState(block: BlockNode) {
		processedBlocks.remove(block)
	}
}
