@file:Suppress("UNCHECKED_CAST")

package jadx.core.dex.visitors.blocks

import jadx.api.plugins.utils.Utils
import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.ExcSplitCrossAttr
import jadx.core.dex.attributes.nodes.TmpEdgeAttr
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.NamedArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.dex.trycatch.ExcHandlerAttr
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.dex.trycatch.TryCatchBlockAttr
import jadx.core.dex.visitors.typeinference.TypeCompare
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnRemover
import jadx.core.utils.ListUtils
import jadx.core.utils.blocks.BlockSet
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.HashSet
import java.util.Objects

/**
 * 异常处理器（try/catch）到控制流图的重建。
 *
 * **背景**：字节码里的 try/catch 只是“某条可能抛异常的指令 -> 处理器块”的边。
 * 要生成源码，需要把 try 区域用“顶部/底部拆分块”（top/bottom splitter）包起来，
 * 再把这些拆分块连到各个 catch 处理器，从而恢复出结构化的 try 区域。
 *
 * **整体流程**（[process]）：
 * 1. 清理无效 catch 属性、初始化处理器块；
 * 2. 把同一处理器的多个 throw 块归并成 [TryCatchBlockAttr]；
 * 3. 合并嵌套/相同的 try 块，识别多 catch（multi-catch）；
 * 4. 对每个 try 块，插入顶部/底部拆分块并连接处理器（[wrapBlocksWithTryCatch]）；
 * 5. 移除未使用的处理器。
 *
 * **Kotlin 转换说明**：原类只有静态方法，转为 `object` + `@JvmStatic`；
 * 所有块身份判断用 `===`；对只读 List 的就地修改用 `as MutableList` 强转
 * （文件级 `@file:Suppress("UNCHECKED_CAST")`）。
 */
object BlockExceptionHandler {

	private val LOG = LoggerFactory.getLogger(BlockExceptionHandler::class.java)

	fun process(mth: MethodNode): Boolean {
		if (mth.isNoExceptionHandlers()) {
			return false
		}
		BlockProcessor.updateCleanSuccessors(mth)
		DominatorTree.computeDominanceFrontier(mth)

		processCatchAttr(mth)
		initExcHandlers(mth)

		val tryBlocks = prepareTryBlocks(mth)
		connectExcHandlers(mth, tryBlocks)
		mth.addAttr(AType.TRY_BLOCKS_LIST, tryBlocks)
		for (block in checkNotNull(mth.basicBlocks)) {
			block.updateCleanSuccessors()
		}

		for (eh in mth.getExceptionHandlers()) {
			removeMonitorExitFromExcHandler(mth, eh)
		}
		BlockProcessor.removeMarkedBlocks(mth)

		val sorted = BlockSet(mth)
		BlockUtils.visitDFS(mth) { b -> sorted.add(b) }
		removeUnusedExcHandlers(mth, tryBlocks, sorted)
		return true
	}

	/**
	 * 把 try 块用顶部/底部拆分块包起来并连到处理器块。
	 * 有时 try 块本身就是处理器块，需要先连接再包装，因此用队列推迟未就绪的 try 块。
	 */
	private fun connectExcHandlers(mth: MethodNode, tryBlocks: List<TryCatchBlockAttr>) {
		if (tryBlocks.isEmpty()) {
			return
		}
		val limit = tryBlocks.size * 3
		var count = 0
		val queue = ArrayDeque(tryBlocks)
		while (!queue.isEmpty()) {
			val tryBlock = queue.removeFirst()
			val complete = wrapBlocksWithTryCatch(mth, tryBlock)
			if (!complete) {
				queue.addLast(tryBlock) // 放回队列末尾稍后重试
			}
			if (count++ > limit) {
				throw JadxRuntimeException("Try blocks wrapping queue limit reached! Please report as an issue!")
			}
		}
	}

	/**
	 * 整理 catch 属性：
	 * 1. 不可能抛异常的指令去掉其 catch 属性；
	 * 2. 若块内所有指令的 catch 属性相同，则把该属性提升到整个块上。
	 */
	private fun processCatchAttr(mth: MethodNode) {
		for (block in checkNotNull(mth.basicBlocks)) {
			for (insn in block.getInstructions()) {
				if (insn.contains(AType.EXC_CATCH) && !insn.canThrowException()) {
					insn.remove(AType.EXC_CATCH)
				}
			}
		}
		for (block in checkNotNull(mth.basicBlocks)) {
			val commonCatchAttr = getCommonCatchAttr(block)
			if (commonCatchAttr != null) {
				block.addAttr(commonCatchAttr)
				for (insn in block.getInstructions()) {
					if (insn.contains(AFlag.TRY_ENTER)) {
						block.add(AFlag.TRY_ENTER)
					}
					if (insn.contains(AFlag.TRY_LEAVE)) {
						block.add(AFlag.TRY_LEAVE)
					}
				}
			}
		}
	}

	private fun getCommonCatchAttr(block: BlockNode): CatchAttr? {
		var commonCatchAttr: CatchAttr? = null
		for (insn in block.getInstructions()) {
			val catchAttr = insn.get(AType.EXC_CATCH)
			if (catchAttr != null) {
				if (commonCatchAttr == null) {
					commonCatchAttr = catchAttr
					continue
				}
				if (commonCatchAttr != catchAttr) {
					return null
				}
			}
		}
		return commonCatchAttr
	}

	/**
	 * 初始化异常处理器块：把带 EXC_HANDLER 属性的首指令转成处理器块，
	 * 收集其支配范围内的所有块；若块已有前驱（已被连接过），则新建一个空的处理器块。
	 */
	private fun initExcHandlers(mth: MethodNode) {
		val blocks = checkNotNull(mth.basicBlocks)
		val blocksCount = blocks.size
		for (i in 0 until blocksCount) { // 循环中会向列表末尾追加新块
			val block = blocks[i]
			val firstInsn = BlockUtils.getFirstInsn(block) ?: continue
			val excHandlerAttr = firstInsn.get(AType.EXC_HANDLER) ?: continue
			firstInsn.remove(AType.EXC_HANDLER)
			removeTmpConnection(block)

			val excHandler = excHandlerAttr.handler
			if (block.getPredecessors().isEmpty()) {
				excHandler.setHandlerBlock(block)
				block.addAttr(excHandlerAttr)
				excHandler.addBlock(block)
				for (b in BlockUtils.collectBlocksDominatedByWithExcHandlers(mth, block, block)) {
					excHandler.addBlock(b)
				}
			} else {
				// 已被连接过的处理器 -> 把 catch 置空
				val emptyHandlerBlock = BlockSplitter.startNewBlock(mth, block.startOffset)
				emptyHandlerBlock.add(AFlag.SYNTHETIC)
				emptyHandlerBlock.addAttr(excHandlerAttr)
				BlockSplitter.connect(emptyHandlerBlock, block)
				excHandler.setHandlerBlock(emptyHandlerBlock)
				excHandler.addBlock(emptyHandlerBlock)
			}
			fixMoveExceptionInsn(block, excHandlerAttr)
		}
	}

	private fun removeTmpConnection(block: BlockNode) {
		val tmpEdgeAttr = block.get(AType.TMP_EDGE)
		if (tmpEdgeAttr != null) {
			// 撤回临时边
			BlockSplitter.removeConnection(tmpEdgeAttr.block, block)
			block.remove(AType.TMP_EDGE)
		}
	}

	/**
	 * 收集每个处理器的 throw 块，生成初始的 try 块列表，并合并嵌套/相同的 try。
	 */
	private fun prepareTryBlocks(mth: MethodNode): List<TryCatchBlockAttr> {
		val blocksByHandler = HashMap<ExceptionHandler, MutableList<BlockNode>>()
		for (block in checkNotNull(mth.basicBlocks)) {
			val catchAttr = block.get(AType.EXC_CATCH)
			if (catchAttr != null) {
				for (eh in catchAttr.handlers) {
					blocksByHandler.computeIfAbsent(eh) { ArrayList() }.add(block)
				}
			}
		}
		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug("Input exception handlers:")
			blocksByHandler.forEach { (eh, blocks) ->
				LOG.debug(" {}, throw blocks: {}, handler blocks: {}", eh, blocks, eh.blocks)
			}
		}
		if (blocksByHandler.isEmpty()) {
			// 没有 catch 块 -> 移除全部处理器
			for (eh in mth.getExceptionHandlers()) {
				removeExcHandler(mth, eh)
			}
		} else {
			// 移除在 catch 属性中没有对应块的处理器
			blocksByHandler.forEach { (eh, blocks) ->
				if (blocks.isEmpty()) {
					removeExcHandler(mth, eh)
				}
			}
		}
		BlockSplitter.detachMarkedBlocks(mth)
		mth.clearExceptionHandlers()
		if (mth.isNoExceptionHandlers()) {
			return Collections.emptyList()
		}

		blocksByHandler.forEach { (eh, blocks) ->
			// 移除同一处理器自身的块
			blocks.removeAll(eh.blocks)
		}

		val tryBlocks = ArrayList<TryCatchBlockAttr>()
		blocksByHandler.forEach { (eh, blocks) ->
			val handlers = ArrayList<ExceptionHandler>(1)
			handlers.add(eh)
			tryBlocks.add(TryCatchBlockAttr(tryBlocks.size, handlers, blocks))
		}
		if (tryBlocks.size > 1) {
			// 合并或标记为外层/内层
			while (true) {
				val restart = combineTryCatchBlocks(tryBlocks)
				if (!restart) {
					break
				}
			}
		}
		checkForMultiCatch(mth, tryBlocks)
		clearTryBlocks(mth, tryBlocks)
		sortHandlers(mth, tryBlocks)

		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug("Result try-catch blocks:")
			tryBlocks.forEach { tryBlock -> LOG.debug(" {}", tryBlock) }
		}
		return tryBlocks
	}

	private fun clearTryBlocks(mth: MethodNode, tryBlocks: MutableList<TryCatchBlockAttr>) {
		tryBlocks.forEach { tc ->
			(tc.getBlocks() as MutableList<BlockNode>).removeIf { b -> b.contains(AFlag.REMOVE) }
		}
		tryBlocks.removeIf { tb -> tb.getBlocks().isEmpty() || tb.handlers.isEmpty() }
		mth.clearExceptionHandlers()
		BlockSplitter.detachMarkedBlocks(mth)
	}

	private fun combineTryCatchBlocks(tryBlocks: MutableList<TryCatchBlockAttr>): Boolean {
		for (outerTryBlock in tryBlocks) {
			for (innerTryBlock in tryBlocks) {
				if (outerTryBlock === innerTryBlock || innerTryBlock.getOuterTryBlock() != null) {
					continue
				}
				if (checkTryCatchRelation(tryBlocks, outerTryBlock, innerTryBlock)) {
					return true
				}
			}
		}
		return false
	}

	private fun checkTryCatchRelation(
		tryBlocks: MutableList<TryCatchBlockAttr>,
		outerTryBlock: TryCatchBlockAttr,
		innerTryBlock: TryCatchBlockAttr,
	): Boolean {
		if (outerTryBlock.getBlocks() == innerTryBlock.getBlocks()) {
			// 相同的 try 块 -> 合并处理器
			val handlers = Utils.concatDistinct(outerTryBlock.handlers, innerTryBlock.handlers)
			tryBlocks.add(TryCatchBlockAttr(tryBlocks.size, handlers, outerTryBlock.getBlocks() as MutableList<BlockNode>))
			tryBlocks.remove(outerTryBlock)
			tryBlocks.remove(innerTryBlock)
			return true
		}

		val handlerBlocks = HashSet<BlockNode>()
		for (eh in innerTryBlock.handlers) {
			handlerBlocks.addAll(eh.blocks)
		}
		var catchInHandler = false
		for (b in handlerBlocks) {
			if (isHandlersIntersects(outerTryBlock, b)) {
				catchInHandler = true
				break
			}
		}
		var catchInTry = false
		for (b in innerTryBlock.getBlocks()) {
			if (isHandlersIntersects(outerTryBlock, b)) {
				catchInTry = true
				break
			}
		}
		var blocksOutsideHandler = false
		for (b in outerTryBlock.getBlocks()) {
			if (!handlerBlocks.contains(b)) {
				blocksOutsideHandler = true
				break
			}
		}

		if (catchInHandler && (catchInTry || blocksOutsideHandler)) {
			// 转换为内层 try
			val mergedBlocks = Utils.concatDistinct(outerTryBlock.getBlocks(), innerTryBlock.getBlocks())
			(innerTryBlock.handlers as MutableList<ExceptionHandler>).removeAll(outerTryBlock.handlers)
			innerTryBlock.setOuterTryBlock(outerTryBlock)
			outerTryBlock.addInnerTryBlock(innerTryBlock)
			outerTryBlock.setBlocks(mergedBlocks)
			return false
		}
		val innerHandlerSet = HashSet(innerTryBlock.handlers)
		if (innerHandlerSet.containsAll(outerTryBlock.handlers)) {
			// 合并
			val mergedBlocks = Utils.concatDistinct(outerTryBlock.getBlocks(), innerTryBlock.getBlocks())
			val handlers = Utils.concatDistinct(outerTryBlock.handlers, innerTryBlock.handlers)
			tryBlocks.add(TryCatchBlockAttr(tryBlocks.size, handlers, mergedBlocks))
			tryBlocks.remove(outerTryBlock)
			tryBlocks.remove(innerTryBlock)
			return true
		}
		return false
	}

	private fun isHandlersIntersects(outerTryBlock: TryCatchBlockAttr, block: BlockNode): Boolean {
		val catchAttr = block.get(AType.EXC_CATCH)
		return catchAttr != null && Objects.equals(catchAttr.handlers, outerTryBlock.handlers)
	}

	private fun removeExcHandler(mth: MethodNode, excHandler: ExceptionHandler) {
		excHandler.markForRemove()
		BlockSplitter.removeConnection(checkNotNull(mth.enterBlock), checkNotNull(excHandler.getHandlerBlock()))
	}

	/**
	 * 对单个 try 块插入顶部/底部拆分块，并把处理器连到拆分块上。
	 *
	 * 返回 false 表示该 try 块暂未就绪（顶部块没有前驱且不是入口），需要稍后重试。
	 */
	private fun wrapBlocksWithTryCatch(mth: MethodNode, tryCatchBlock: TryCatchBlockAttr): Boolean {
		val blocks = tryCatchBlock.getBlocks()
		val top = searchTopBlock(mth, blocks)
		if (top.getPredecessors().isEmpty() && top !== mth.enterBlock) {
			return false
		}
		var bottom = searchBottomBlock(mth, blocks)
		val splitReturn: BlockNode?
		if (bottom != null && bottom.isReturnBlock()) {
			if (Consts.DEBUG_EXC_HANDLERS) {
				LOG.debug("TryCatch #{} bottom block ({}) is return, split", tryCatchBlock.id(), bottom)
			}
			splitReturn = bottom
			val newBottom = BlockSplitter.blockSplitTop(mth, bottom)
			newBottom.add(AFlag.SYNTHETIC)
			bottom = newBottom
		} else {
			splitReturn = null
		}
		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug("TryCatch #{} split: top {}, bottom: {}", tryCatchBlock.id(), top, bottom)
		}
		val topSplitterBlock = getTopSplitterBlock(mth, top)
		topSplitterBlock.add(AFlag.EXC_TOP_SPLITTER)
		topSplitterBlock.add(AFlag.SYNTHETIC)

		var totalHandlerBlocks = 0
		for (eh in tryCatchBlock.handlers) {
			totalHandlerBlocks += eh.blocks.size
		}

		val bottomSplitterBlock: BlockNode?
		if (bottom == null || totalHandlerBlocks == 0) {
			bottomSplitterBlock = null
		} else {
			val existBottomSplitter = BlockUtils.getBlockWithFlag(bottom.getSuccessors(), AFlag.EXC_BOTTOM_SPLITTER)
			val newBottomSplitter = existBottomSplitter ?: BlockSplitter.startNewBlock(mth, -1)
			newBottomSplitter.add(AFlag.EXC_BOTTOM_SPLITTER)
			newBottomSplitter.add(AFlag.SYNTHETIC)
			BlockSplitter.connect(bottom, newBottomSplitter)
			bottomSplitterBlock = newBottomSplitter
			if (splitReturn != null) {
				// 把处理器重定向到原始 return 块，避免合成块自环
				val bottomPreds = BlockSet.from(mth, bottom.getPredecessors())
				for (handler in tryCatchBlock.handlers) {
					if (bottomPreds.intersects(handler.blocks)) {
						val lastBlock = bottomPreds.intersect(handler.blocks).one
						if (lastBlock != null) {
							BlockSplitter.replaceConnection(lastBlock, bottom, splitReturn)
						}
					}
				}
			}
		}

		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug(
				"TryCatch #{} result splitters: top {}, bottom: {}",
				tryCatchBlock.id(),
				topSplitterBlock,
				bottomSplitterBlock,
			)
		}
		connectSplittersAndHandlers(tryCatchBlock, topSplitterBlock, bottomSplitterBlock)

		// 插入新底部后，原来指向 bottom 的交叉边可能被误认为回边（看起来像循环）。
		// 这里把“既从 bottom 可达、又是 bottom 前驱”的边改指向原始路径交叉点，消除假循环。
		if (bottom != null && bottom.contains(AType.EXC_SPLIT_CROSS)) {
			val convertBlocks = ArrayList<BlockNode>()
			for (b in bottom.getPredecessors()) {
				if (BlockUtils.isAnyPathExists(bottom, b)) {
					convertBlocks.add(b)
				}
			}
			for (b in convertBlocks) {
				// 不能在第一个循环里直接替换，否则会修改前驱列表
				BlockSplitter.replaceConnection(
					b,
					bottom,
					checkNotNull(bottom.get(AType.EXC_SPLIT_CROSS)).originalPathCross,
				)
			}
		}

		for (block in blocks) {
			val currentTCBAttr = block.get(AType.TRY_BLOCK)
			if (currentTCBAttr == null || currentTCBAttr.getInnerTryBlocks().contains(tryCatchBlock)) {
				block.addAttr(tryCatchBlock)
			}
		}
		tryCatchBlock.setTopSplitter(topSplitterBlock)

		topSplitterBlock.updateCleanSuccessors()
		if (bottomSplitterBlock != null) {
			bottomSplitterBlock.updateCleanSuccessors()
		}
		return true
	}

	private fun getTopSplitterBlock(mth: MethodNode, top: BlockNode): BlockNode {
		if (top === mth.enterBlock) {
			val fixedTop = checkNotNull(mth.enterBlock).getSuccessors()[0]
			return BlockSplitter.blockSplitTop(mth, fixedTop)
		}
		val existPredTopSplitter = BlockUtils.getBlockWithFlag(top.getPredecessors(), AFlag.EXC_TOP_SPLITTER)
		if (existPredTopSplitter != null) {
			return existPredTopSplitter
		}
		// 尝试复用顶部块下方空简单路径上已有的拆分块
		val cleanSuccessors = checkNotNull(top.getCleanSuccessors())
		if (cleanSuccessors.size == 1 && top.getInstructions().isEmpty()) {
			val otherTopSplitter = BlockUtils.getBlockWithFlag(cleanSuccessors, AFlag.EXC_TOP_SPLITTER)
			if (otherTopSplitter != null && otherTopSplitter.getPredecessors().size == 1) {
				return otherTopSplitter
			}
		}
		return BlockSplitter.blockSplitTop(mth, top)
	}

	private fun searchTopBlock(mth: MethodNode, blocks: List<BlockNode>): BlockNode {
		val top = BlockUtils.getTopBlock(blocks)
		if (top != null) {
			return adjustTopBlock(top)
		}
		val topDom = BlockUtils.getCommonDominator(mth, blocks)
		if (topDom != null) {
			// 若集合已包含支配者，公共支配会多返回上一级块，此时取其后继
			if (topDom.getSuccessors().size == 1) {
				val upBlock = topDom.getSuccessors()[0]
				if (blocks.contains(upBlock)) {
					return upBlock
				}
			}
			return adjustTopBlock(topDom)
		}
		throw JadxRuntimeException("Failed to find top block for try-catch from: $blocks")
	}

	private fun adjustTopBlock(topBlock: BlockNode): BlockNode {
		if (topBlock.getSuccessors().size == 1 && !topBlock.contains(AType.EXC_CATCH)) {
			// 顶部块可能被其他异常处理器抬高了，这里尝试回退一层
			return topBlock.getSuccessors()[0]
		}
		return topBlock
	}

	private fun searchBottomBlock(mth: MethodNode, blocks: List<BlockNode>): BlockNode? {
		// 先在输入集合内找公共后支配块
		val bottom = BlockUtils.getBottomBlock(blocks)
		if (bottom != null) {
			return bottom
		}
		// 找不到 -> 集合没有共同后支配者，尝试在集合外找公共交叉块
		// 注意：出口节点不需要底部块（它们没有数据流出）
		val pathCross = BlockUtils.getPathCross(mth, blocks) ?: return null
		val preds = ArrayList(pathCross.getPredecessors())
		preds.removeAll(blocks)
		val outsidePredecessors = ArrayList<BlockNode>()
		for (p in preds) {
			if (!BlockUtils.atLeastOnePathExists(blocks, p)) {
				outsidePredecessors.add(p)
			}
		}
		// 没有集合外前驱，或全部前驱都在集合外（插入合成块没意义）-> 直接返回交叉块
		if (outsidePredecessors.isEmpty() || outsidePredecessors.size == pathCross.getPredecessors().size) {
			return pathCross
		}
		// 只有部分前驱在集合外 -> 只为集合内路径拆分
		val splitCross = BlockSplitter.blockSplitTop(mth, pathCross)
		splitCross.add(AFlag.SYNTHETIC)
		splitCross.addAttr(ExcSplitCrossAttr(pathCross))
		for (outsidePredecessor in outsidePredecessors) {
			// 把集合外的前驱还原到原始交叉块
			BlockSplitter.replaceConnection(outsidePredecessor, splitCross, pathCross)
		}
		return splitCross
	}

	private fun connectSplittersAndHandlers(
		tryCatchBlock: TryCatchBlockAttr,
		topSplitterBlock: BlockNode,
		bottomSplitterBlock: BlockNode?,
	) {
		for (handler in tryCatchBlock.handlers) {
			val handlerBlock = checkNotNull(handler.getHandlerBlock())
			BlockSplitter.connect(topSplitterBlock, handlerBlock)
			if (bottomSplitterBlock != null) {
				BlockSplitter.connect(bottomSplitterBlock, handlerBlock)
			}
		}
		val outerTryBlock = tryCatchBlock.getOuterTryBlock()
		if (outerTryBlock != null) {
			connectSplittersAndHandlers(outerTryBlock, topSplitterBlock, bottomSplitterBlock)
		}
	}

	private fun fixMoveExceptionInsn(block: BlockNode, excHandlerAttr: ExcHandlerAttr) {
		val excHandler = excHandlerAttr.handler
		val argType = excHandler.argType
		val me = BlockUtils.getLastInsn(block)
		if (me != null && me.type == InsnType.MOVE_EXCEPTION) {
			// 为 move-exception 设置正确的异常类型
			val resArg = InsnArg.reg(checkNotNull(me.getResult()).regNum, argType)
			resArg.copyAttributesFrom(me)
			me.setResult(resArg)
			me.add(AFlag.DONT_INLINE)
			resArg.add(AFlag.CUSTOM_DECLARE)
			excHandler.setArg(resArg)
			me.addAttr(excHandlerAttr)
			return
		}
		// 处理器参数未被使用
		excHandler.setArg(NamedArg("unused", argType))
	}

	/** 删除异常处理器中 MONITOR_ENTER 之前的所有 MONITOR_EXIT（避免重复解锁）。 */
	private fun removeMonitorExitFromExcHandler(mth: MethodNode, excHandler: ExceptionHandler) {
		for (excBlock in excHandler.blocks) {
			val remover = InsnRemover(mth, excBlock)
			for (insn in excBlock.getInstructions()) {
				if (insn.type == InsnType.MONITOR_ENTER) {
					break
				}
				if (insn.type == InsnType.MONITOR_EXIT) {
					remover.addAndUnbind(insn)
				}
			}
			remover.perform()
		}
	}

	private fun checkForMultiCatch(mth: MethodNode, tryBlocks: List<TryCatchBlockAttr>) {
		var merged = false
		for (tryBlock in tryBlocks) {
			if (mergeMultiCatch(mth, tryBlock)) {
				merged = true
			}
		}
		if (merged) {
			BlockSplitter.detachMarkedBlocks(mth)
			mth.clearExceptionHandlers()
		}
	}

	/**
	 * 尝试把多个处理器合并成 Java 7 的 multi-catch。
	 * 条件：每个处理器只有一个块、块内只有一条 move-exception、且都跳到同一个后继块。
	 */
	private fun mergeMultiCatch(mth: MethodNode, tryCatch: TryCatchBlockAttr): Boolean {
		if (tryCatch.handlers.size < 2) {
			return false
		}
		for (handler in tryCatch.handlers) {
			if (handler.blocks.size != 1) {
				return false
			}
			val block = checkNotNull(handler.getHandlerBlock())
			if (block.getInstructions().size != 1 ||
				!BlockUtils.checkLastInsnType(block, InsnType.MOVE_EXCEPTION)
			) {
				return false
			}
		}
		val handlerBlocks = ArrayList<BlockNode>(tryCatch.handlers.size)
		for (handler in tryCatch.handlers) {
			handlerBlocks.add(checkNotNull(handler.getHandlerBlock()))
		}
		val successorBlocks = ArrayList<BlockNode>()
		for (h in handlerBlocks) {
			for (s in h.getSuccessors()) {
				if (!successorBlocks.contains(s)) {
					successorBlocks.add(s)
				}
			}
		}
		if (successorBlocks.size != 1) {
			return false
		}
		val successorBlock = successorBlocks[0]
		if (!ListUtils.unorderedEquals(successorBlock.getPredecessors(), handlerBlocks)) {
			return false
		}
		val regs = ArrayList<RegisterArg?>()
		for (h in tryCatch.handlers) {
			val result = checkNotNull(BlockUtils.getLastInsn(h.getHandlerBlock())).getResult()
			if (!regs.contains(result)) {
				regs.add(result)
			}
		}
		if (regs.size != 1) {
			return false
		}

		// 确认可合并：只保留第一个处理器，其余移除
		val resultHandler = tryCatch.handlers[0]
		(tryCatch.handlers as MutableList<ExceptionHandler>).removeIf { handler ->
			if (handler === resultHandler) {
				false
			} else {
				resultHandler.addCatchTypes(mth, handler.catchTypes)
				handler.markForRemove()
				true
			}
		}
		return true
	}

	/** 按异常类型（以及同名冲突时的类名）对处理器排序。 */
	private fun sortHandlers(mth: MethodNode, tryBlocks: List<TryCatchBlockAttr>) {
		val typeCompare = mth.root().typeCompare
		val comparator = typeCompare.reversedComparator
		val catchTypesComparator = Comparator<ClassInfo> { first, second -> compareByTypeAndName(comparator, first, second) }
		for (tryBlock in tryBlocks) {
			for (handler in tryBlock.handlers) {
				Collections.sort(handler.catchTypes, catchTypesComparator)
			}
			val handlerComparator = Comparator<ExceptionHandler> { first, second ->
				if (first == second) {
					throw JadxRuntimeException("Same handlers in try block: $tryBlock")
				}
				if (first.isCatchAll()) {
					1
				} else if (second.isCatchAll()) {
					-1
				} else {
					compareByTypeAndName(
						comparator,
						ListUtils.first(first.catchTypes),
						ListUtils.first(second.catchTypes),
					)
				}
			}
			Collections.sort(tryBlock.handlers, handlerComparator)
		}
	}

	private fun compareByTypeAndName(comparator: Comparator<ArgType>, first: ClassInfo, second: ClassInfo): Int {
		val r = comparator.compare(first.type, second.type)
		if (r == -2) {
			// 类型比较冲突时按名称排序
			return first.compareTo(second)
		}
		return r
	}

	/**
	 * 移除连接时未用到的异常处理器。
	 * 先检查这些处理器块是否已经不可达。
	 */
	private fun removeUnusedExcHandlers(mth: MethodNode, tryBlocks: List<TryCatchBlockAttr>, blocks: BlockSet) {
		for (eh in mth.getExceptionHandlers()) {
			var notProcessed = true
			val handlerBlock = eh.getHandlerBlock()
			if (handlerBlock == null || blocks.contains(handlerBlock)) {
				continue
			}
			for (tcb in tryBlocks) {
				if (tcb.handlers.contains(eh)) {
					notProcessed = false
					break
				}
			}
			if (notProcessed) {
				BlockProcessor.removeUnreachableBlock(handlerBlock, mth)
			}
		}
	}
}
