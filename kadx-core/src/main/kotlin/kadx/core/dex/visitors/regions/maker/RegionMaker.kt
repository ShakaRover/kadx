package kadx.core.dex.visitors.regions.maker

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.EdgeInsnAttr
import kadx.core.dex.attributes.nodes.LoopInfo
import kadx.core.dex.instructions.IfNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.SwitchInsn
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnContainer
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.Region
import kadx.core.utils.BlockUtils
import kadx.core.utils.blocks.BlockSet
import kadx.core.utils.exceptions.KadxOverflowException

/** 单块允许被纳入区域树的最大总次数（首次 + 复制）；超限后丢弃重复区域（防止重处理级联爆炸） */
private const val MAX_BLOCK_INCLUSIONS = 6

/** 单区域内同一块作为「下一块」的最大重复次数（游走无进度守卫） */
private const val MAX_WALK_REPEATS = 6

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

	/** 每块已被重复加入区域的次数 */
	private val dupBlockCounts = HashMap<BlockNode, Int>()

	fun makeMthRegion(): Region = makeRegion(checkNotNull(mth.enterBlock))

	fun makeRegion(startBlock: BlockNode): Region {
		val region = Region(stack.peekRegion())
		if (stack.containsExit(startBlock)) {
			insertEdgeInsns(region, startBlock)
			return region
		}
		if (processedBlocks.addChecked(startBlock)) {
			// 同一个块被再次作为区域起点（addChecked 返回 true=已处理过）：上游 #2784 会无配额地
			// 复制代码并继续处理，修复了「代码丢失」问题，但重复处理会级联放大——区域树
			// 指数膨胀（CoreTextFieldKt：511 块 → 22.8 万区域节点）并触发 Regions limit / SOE。
			// 限制每块纳入次数，超限后回退 1.5.3 语义：丢弃本次重复区域。
			if (!tryIncludeBlock(startBlock)) {
				mth.addWarnComment("Removed duplicated region for block: " + startBlock + ' ' + startBlock.getAttributesString())
				return region
			}
			if (!startBlock.contains(AFlag.DUPLICATED)) {
				mth.addWarnComment("Code duplicated, block: " + startBlock + ' ' + startBlock.getAttributesString())
				startBlock.add(AFlag.DUPLICATED)
			}
		} else {
			tryIncludeBlock(startBlock)
		}
		// 游走进度守卫：同一块作为「下一块」重复出现超过 [MAX_WALK_REPEATS] 次说明
		// 游走陷入 ADDED_TO_REGION 块组成的环（无进度、不消耗纳入预算）——
		// 终止本区域游走，regions count limit 由此结构性不可达
		val walkRepeats = HashMap<BlockNode, Int>()
		var next: BlockNode? = startBlock
		while (next != null) {
			next = traverse(region, next)
			regionsCount++
			if (regionsCount > regionsLimit) {
				throw KadxOverflowException("Regions count limit reached at block " + startBlock)
			}
			if (next != null) {
				val rep = (walkRepeats[next] ?: 0) + 1
				walkRepeats[next] = rep
				if (rep > MAX_WALK_REPEATS) {
					mth.addWarnComment(
						"Region walk repeated block " + next + " without progress, region truncated",
					)
					break
				}
			}
		}
		return region
	}

	/** 记录并检查块的纳入次数；返回 false 表示超出 [MAX_BLOCK_INCLUSIONS]，应丢弃本次纳入 */
	private fun tryIncludeBlock(block: BlockNode): Boolean {
		val count = (dupBlockCounts[block] ?: 0) + 1
		dupBlockCounts[block] = count
		return count <= MAX_BLOCK_INCLUSIONS
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
			if (!tryIncludeBlock(block)) {
				// 超出纳入配额：停止本路径游走（块保持在先前的纳入位置），防止 regionsCount 无界增长
				mth.addWarnComment("Removed duplicated region for block: " + block + ' ' + block.getAttributesString())
				return null
			}
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
