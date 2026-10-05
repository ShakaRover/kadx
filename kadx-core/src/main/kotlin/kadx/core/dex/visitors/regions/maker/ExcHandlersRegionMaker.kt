package kadx.core.dex.visitors.regions.maker

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.Region
import kadx.core.dex.trycatch.ExceptionHandler
import kadx.core.dex.trycatch.TryCatchBlockAttr
import kadx.core.utils.BlockUtils
import kadx.core.utils.RegionUtils

/**
 * 为异常处理器构建区域。
 *
 * **算法意图**：每个 catch/finally 处理器块也需要被包装成区域，才能生成 `try/catch`。
 * 本类：
 * 1. [collectHandlerRegions]：对每个 try 块，找出各处理器与 try 体的交叉块（exit），
 *    再逐个调用 [processExcHandler] 构建处理器区域；
 * 2. [processHandlersOutBlocks]：收集处理器后继中“尚未被任何区域包含”的块，
 *    单独建一个区域，避免这些块丢失。
 *
 * Kotlin 转换说明：`handler.setHandlerRegion`、`stack.addExit` 等保持原方法名；
 * 对象引用比较用 `===`。
 */
class ExcHandlersRegionMaker(private val mth: MethodNode, private val regionMaker: RegionMaker) {

	fun process() {
		if (mth.isNoExceptionHandlers()) {
			return
		}
		val excOutBlock = collectHandlerRegions()
		if (excOutBlock != null) {
			checkNotNull(mth.region).add(excOutBlock)
		}
	}

	private fun collectHandlerRegions(): IRegion? {
		val tcs = mth.getAll(AType.TRY_BLOCKS_LIST)
		for (tc in tcs) {
			val blocks = ArrayList<BlockNode>(tc.handlersCount)
			val splitters: MutableSet<BlockNode> = HashSet()
			for (handler in tc.handlers) {
				val handlerBlock = handler.getHandlerBlock()
				if (handlerBlock != null) {
					blocks.add(handlerBlock)
					splitters.add(BlockUtils.getTopSplitterForHandler(handlerBlock))
				} else {
					mth.addDebugComment("No exception handler block: " + handler)
				}
			}
			val exits: MutableSet<BlockNode> = HashSet()
			for (splitter in splitters) {
				for (handler in blocks) {
					if (handler.contains(AFlag.REMOVE)) {
						continue
					}
					val s = splitter.successors
					if (s.isEmpty()) {
						mth.addDebugComment("No successors for splitter: " + splitter)
						continue
					}
					val ss = s[0]
					val cross = BlockUtils.getPathCross(mth, ss, handler)
					if (cross != null && cross !== ss && cross !== handler) {
						exits.add(cross)
					}
				}
			}
			for (handler in tc.handlers) {
				processExcHandler(handler, exits)
			}
		}
		return processHandlersOutBlocks(tcs)
	}

	/** 查找尚未被任何区域包含的处理器后继块 */
	private fun processHandlersOutBlocks(tcs: List<TryCatchBlockAttr>): IRegion? {
		val allRegionBlocks: MutableSet<IBlock> = HashSet()
		RegionUtils.getAllRegionBlocks(checkNotNull(mth.region), allRegionBlocks)

		val successorBlocks: MutableSet<IBlock> = HashSet()
		for (tc in tcs) {
			for (handler in tc.handlers) {
				val region = handler.getHandlerRegion()
				if (region != null) {
					val lastBlock = RegionUtils.getLastBlock(region)
					if (lastBlock is BlockNode) {
						successorBlocks.addAll(lastBlock.successors)
					}
					RegionUtils.getAllRegionBlocks(region, allRegionBlocks)
				}
			}
		}
		successorBlocks.removeAll(allRegionBlocks)
		if (successorBlocks.isEmpty()) {
			return null
		}
		val stack = regionMaker.stack
		val excOutRegion = Region(mth.region)
		for (block in successorBlocks) {
			if (block is BlockNode) {
				stack.clear()
				stack.push(excOutRegion)
				excOutRegion.add(regionMaker.makeRegion(block))
			}
		}
		return excOutRegion
	}

	private fun processExcHandler(handler: ExceptionHandler, exits: Set<BlockNode>) {
		val start = handler.getHandlerBlock() ?: return
		val stack = regionMaker.stack.clear()
		val dom: BlockNode
		if (handler.isFinally()) {
			dom = BlockUtils.getTopSplitterForHandler(start)
		} else {
			dom = start
			stack.addExits(exits)
		}
		if (dom.contains(AFlag.REMOVE)) {
			return
		}
		val handlerExits = ArrayList<BlockNode>()

		val handlerOutBlock = BlockUtils.getTryAndHandlerCrossBlock(mth, handler)
		if (handlerOutBlock != null) {
			// 确保 frontier 的其他前驱来自 try 末尾
			handlerExits.add(handlerOutBlock)
		} else {
			// 退化为简单 frontier
			handlerExits.addAll(BlockUtils.bitSetToBlocks(mth, dom.domFrontier))
		}

		val inLoop = mth.getLoopForBlock(start) != null
		for (exit in handlerExits) {
			if ((!inLoop || BlockUtils.isPathExists(start, exit)) &&
				RegionUtils.isRegionContainsBlock(checkNotNull(mth.region), exit)
			) {
				stack.addExit(exit)
			}
		}
		handler.setHandlerRegion(regionMaker.makeRegion(start))

		val excHandlerAttr = start.get(AType.EXC_HANDLER)
		if (excHandlerAttr == null) {
			mth.addWarn("Missing exception handler attribute for start block: " + start)
		} else {
			checkNotNull(handler.getHandlerRegion()).addAttr(excHandlerAttr)
		}
	}
}
