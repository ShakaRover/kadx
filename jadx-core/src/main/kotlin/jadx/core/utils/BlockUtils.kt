package jadx.core.utils

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.attributes.nodes.PhiListAttr
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.mods.TernaryInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.Edge
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.conditions.IfCondition
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.utils.blocks.BlockSet
import jadx.core.utils.blocks.DFSIteration
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Arrays
import java.util.BitSet
import java.util.Collections
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.LinkedList
import java.util.Objects

/**
 * 基本块（CFG）操作工具集。
 *
 * **用途**：查找/替换指令、位图集合转换、支配关系与路径判断、DFS/BFS 遍历等，
 * 是控制流重建的核心辅助类。
 *
 * **Kotlin 转换说明**：全部为静态方法，用 `object` + `@JvmStatic`。
 */
object BlockUtils {

	/** 空安全的 clean successors 访问（块处理前可能为 null）。 */
	private fun cleanSuccessors(block: BlockNode): List<BlockNode> = block.cleanSuccessors ?: emptyList()

	fun getBlockByOffset(offset: Int, casesBlocks: Iterable<BlockNode>): BlockNode {
		for (block in casesBlocks) {
			if (block.startOffset == offset) {
				return block
			}
		}
		throw JadxRuntimeException(
			"Can't find block by offset: " + InsnUtils.formatOffset(offset) + " in list " + casesBlocks,
		)
	}

	fun selectOther(node: BlockNode, blocks: List<BlockNode>): BlockNode {
		var list = blocks
		if (list.size > 2) {
			list = cleanBlockList(list)
		}
		if (list.size != 2) {
			throw JadxRuntimeException("Incorrect nodes count for selectOther: $node in $list")
		}
		val first = list[0]
		return if (first !== node) {
			first
		} else {
			list[1]
		}
	}

	fun selectOtherSafe(node: BlockNode, blocks: List<BlockNode>): BlockNode? {
		val size = blocks.size
		if (size == 1) {
			val first = blocks[0]
			return if (first !== node) first else null
		}
		if (size == 2) {
			val first = blocks[0]
			return if (first !== node) first else blocks[1]
		}
		return null
	}

	fun isExceptionHandlerPath(b: BlockNode): Boolean {
		if (b.contains(AType.EXC_HANDLER) ||
			b.contains(AFlag.EXC_BOTTOM_SPLITTER) ||
			b.contains(AFlag.REMOVE)
		) {
			return true
		}
		if (b.contains(AFlag.SYNTHETIC)) {
			val s = b.successors
			return s.size == 1 && s[0].contains(AType.EXC_HANDLER)
		}
		return false
	}

	/**
	 * 从块列表中移除异常处理路径。
	 */
	private fun cleanBlockList(list: List<BlockNode>): List<BlockNode> {
		val ret = ArrayList<BlockNode>(list.size)
		for (block in list) {
			if (!isExceptionHandlerPath(block)) {
				ret.add(block)
			}
		}
		return ret
	}

	/**
	 * 从位图集合中清除异常处理路径对应的块。
	 */
	fun cleanBitSet(mth: MethodNode, bs: BitSet) {
		var i = bs.nextSetBit(0)
		while (i >= 0) {
			val block = checkNotNull(mth.basicBlocks)[i]
			if (isExceptionHandlerPath(block)) {
				bs.clear(i)
			}
			i = bs.nextSetBit(i + 1)
		}
	}

	fun isBackEdge(from: BlockNode, to: BlockNode?): Boolean {
		if (to == null) {
			return false
		}
		if (cleanSuccessors(from).contains(to)) {
			return false // 已检查过
		}
		return from.successors.contains(to)
	}

	fun isFollowBackEdge(block: BlockNode?): Boolean {
		if (block == null) {
			return false
		}
		if (block.contains(AFlag.LOOP_START)) {
			val predecessors = block.predecessors
			if (predecessors.size == 1) {
				val loopEndBlock = predecessors[0]
				if (loopEndBlock.contains(AFlag.LOOP_END)) {
					val loops = loopEndBlock.getAll(AType.LOOP)
					for (loop in loops) {
						if (loop.start == block && loop.end == loopEndBlock) {
							return true
						}
					}
				}
			}
		}
		return false
	}

	/**
	 * 判断块中是否包含指定指令（用 `===` 按引用比较）。
	 */
	fun blockContains(block: BlockNode, insn: InsnNode): Boolean {
		for (bi in block.instructions) {
			if (bi === insn) {
				return true
			}
		}
		return false
	}

	fun checkFirstInsn(block: IBlock, predicate: (InsnNode) -> Boolean): Boolean {
		val insn = getFirstInsn(block)
		return insn != null && predicate(insn)
	}

	fun checkLastInsnType(block: IBlock, expectedType: InsnType): Boolean {
		val insn = getLastInsn(block)
		return insn != null && insn.type == expectedType
	}

	fun getLastInsnWithType(block: IBlock, expectedType: InsnType): InsnNode? {
		val insn = getLastInsn(block)
		if (insn != null && insn.type == expectedType) {
			return insn
		}
		return null
	}

	fun getFirstSourceLine(block: IBlock): Int {
		for (insn in block.instructions) {
			val line = insn.sourceLine
			if (line != 0) {
				return line
			}
		}
		return 0
	}

	fun getFirstInsn(block: IBlock?): InsnNode? {
		if (block == null) {
			return null
		}
		val insns = block.instructions
		if (insns.isEmpty()) {
			return null
		}
		return insns[0]
	}

	fun getLastInsn(block: IBlock?): InsnNode? {
		if (block == null) {
			return null
		}
		val insns = block.instructions
		if (insns.isEmpty()) {
			return null
		}
		return insns[insns.size - 1]
	}

	fun isExitBlock(mth: MethodNode, block: BlockNode): Boolean {
		if (block === mth.exitBlock) {
			return true
		}
		return isExitBlock(block)
	}

	fun isExitBlock(block: BlockNode): Boolean {
		val successors = block.successors
		if (successors.isEmpty()) {
			return true
		}
		if (successors.size == 1) {
			val next = successors[0]
			return next.successors.isEmpty()
		}
		return false
	}

	fun containsExitInsn(block: IBlock?): Boolean {
		val lastInsn = getLastInsn(block) ?: return false
		val type = lastInsn.type
		return type == InsnType.RETURN ||
			type == InsnType.THROW ||
			type == InsnType.BREAK ||
			type == InsnType.CONTINUE
	}

	fun getBlockByInsn(mth: MethodNode, insn: InsnNode?): BlockNode? = getBlockByInsn(mth, insn, checkNotNull(mth.basicBlocks))

	fun getBlockByInsn(mth: MethodNode, insn: InsnNode?, blocks: List<BlockNode>): BlockNode? {
		if (insn == null) {
			return null
		}
		if (insn is PhiInsn) {
			return searchBlockWithPhi(mth, insn)
		}
		if (insn.contains(AFlag.WRAPPED)) {
			return getBlockByWrappedInsn(mth, insn)
		}
		for (bn in blocks) {
			if (blockContains(bn, insn)) {
				return bn
			}
		}
		return null
	}

	fun searchBlockWithPhi(mth: MethodNode, insn: PhiInsn): BlockNode? {
		for (block in checkNotNull(mth.basicBlocks)) {
			val phiListAttr = block.get(AType.PHI_LIST)
			if (phiListAttr != null) {
				for (phiInsn in phiListAttr.list) {
					if (phiInsn === insn) {
						return block
					}
				}
			}
		}
		return null
	}

	private fun getBlockByWrappedInsn(mth: MethodNode, insn: InsnNode): BlockNode? {
		for (bn in checkNotNull(mth.basicBlocks)) {
			for (bi in bn.instructions) {
				if (bi === insn || foundWrappedInsn(bi, insn) != null) {
					return bn
				}
			}
		}
		return null
	}

	fun searchInsnParent(mth: MethodNode, insn: InsnNode): InsnNode? {
		val insnArg = searchWrappedInsnParent(mth, insn) ?: return null
		return insnArg.getParentInsn()
	}

	fun searchWrappedInsnParent(mth: MethodNode, insn: InsnNode): InsnArg? {
		if (!insn.contains(AFlag.WRAPPED)) {
			return null
		}
		for (bn in checkNotNull(mth.basicBlocks)) {
			for (bi in bn.instructions) {
				val res = foundWrappedInsn(bi, insn)
				if (res != null) {
					return res
				}
			}
		}
		return null
	}

	private fun foundWrappedInsn(container: InsnNode, insn: InsnNode): InsnArg? {
		for (arg in container.getArguments()) {
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				if (wrapInsn === insn) {
					return arg
				}
				val res = foundWrappedInsn(wrapInsn, insn)
				if (res != null) {
					return res
				}
			}
		}
		if (container is TernaryInsn) {
			return foundWrappedInsnInCondition(container.condition, insn)
		}
		return null
	}

	private fun foundWrappedInsnInCondition(cond: IfCondition, insn: InsnNode): InsnArg? {
		if (cond.isCompare()) {
			val cmpInsn = checkNotNull(cond.compare).insn
			return foundWrappedInsn(cmpInsn, insn)
		}
		for (nestedCond in cond.args) {
			val res = foundWrappedInsnInCondition(nestedCond, insn)
			if (res != null) {
				return res
			}
		}
		return null
	}

	fun newBlocksBitSet(mth: MethodNode): BitSet = BitSet(checkNotNull(mth.basicBlocks).size)

	fun copyBlocksBitSet(mth: MethodNode, bitSet: BitSet): BitSet {
		val copy = BitSet(checkNotNull(mth.basicBlocks).size)
		if (!bitSet.isEmpty()) {
			copy.or(bitSet)
		}
		return copy
	}

	fun blocksToBitSet(mth: MethodNode, blocks: Collection<BlockNode>): BitSet {
		val bs = newBlocksBitSet(mth)
		for (block in blocks) {
			bs.set(block.id)
		}
		return bs
	}

	fun bitSetToOneBlock(mth: MethodNode, bs: BitSet?): BlockNode? {
		if (bs == null || bs.cardinality() != 1) {
			return null
		}
		return checkNotNull(mth.basicBlocks)[bs.nextSetBit(0)]
	}

	fun bitSetToBlocks(mth: MethodNode, bs: BitSet?): List<BlockNode> {
		if (bs == null || bs === EmptyBitSet.EMPTY) {
			return Collections.emptyList()
		}
		val size = bs.cardinality()
		if (size == 0) {
			return Collections.emptyList()
		}
		val blocks = ArrayList<BlockNode>(size)
		var i = bs.nextSetBit(0)
		while (i >= 0) {
			val block = checkNotNull(mth.basicBlocks)[i]
			blocks.add(block)
			i = bs.nextSetBit(i + 1)
		}
		return blocks
	}

	fun forEachBlockFromBitSet(mth: MethodNode, bs: BitSet?, consumer: (BlockNode) -> Unit) {
		if (bs == null || bs === EmptyBitSet.EMPTY || bs.isEmpty()) {
			return
		}
		val blocks = checkNotNull(mth.basicBlocks)
		var i = bs.nextSetBit(0)
		while (i >= 0) {
			consumer(blocks[i])
			i = bs.nextSetBit(i + 1)
		}
	}

	/**
	 * 返回第一个非异常处理、且不沿回边的前驱块。
	 */
	fun getNextBlock(block: BlockNode): BlockNode? {
		val s = cleanSuccessors(block)
		return if (s.isEmpty()) null else s[0]
	}

	fun getPrevBlock(block: BlockNode): BlockNode? {
		val preds = block.predecessors
		return if (preds.size == 1) preds[0] else null
	}

	/**
	 * 返回通向 pathEnd 的路径上的后继块。
	 */
	fun getNextBlockToPath(block: BlockNode, pathEnd: BlockNode): BlockNode? {
		val successors = cleanSuccessors(block)
		if (successors.contains(pathEnd)) {
			return pathEnd
		}
		val path = getAllPathsBlocks(block, pathEnd)
		for (s in successors) {
			if (path.contains(s)) {
				return s
			}
		}
		return null
	}

	/**
	 * 返回从 pathStart 出发路径上的前驱块。
	 */
	fun getPrevBlockOnPath(mth: MethodNode, block: BlockNode, pathStart: BlockNode): BlockNode? {
		val preds = BlockSet.from(mth, block.predecessors)
		if (preds.contains(pathStart)) {
			return pathStart
		}
		val dfs = DFSIteration(mth, pathStart) { b -> cleanSuccessors(b) ?: emptyList() }
		while (true) {
			val next = dfs.next() ?: return null
			if (preds.contains(next)) {
				return next
			}
		}
	}

	/**
	 * 访问从 start 到 end 的任意一条路径上的块（只访问一条路径）。
	 */
	fun visitBlocksOnPath(mth: MethodNode, start: BlockNode, end: BlockNode, visitor: (BlockNode) -> Unit): Boolean {
		visitor(start)
		if (start === end) {
			return true
		}
		if (cleanSuccessors(start).contains(end)) {
			visitor(end)
			return true
		}
		// 对 clean 后继做 DFS
		val visited = newBlocksBitSet(mth)
		val queue = ArrayDeque<BlockNode>()
		queue.addLast(start)
		while (true) {
			val current = queue.peekLast() ?: return false
			var added = false
			for (next in cleanSuccessors(current)) {
				if (next === end) {
					queue.removeFirst() // start 已访问
					queue.addLast(next)
					for (b in queue) {
						visitor(b)
					}
					return true
				}
				val id = next.id
				if (!visited.get(id)) {
					visited.set(id)
					queue.addLast(next)
					added = true
					break
				}
			}
			if (!added) {
				queue.pollLast()
				if (queue.isEmpty()) {
					return false
				}
			}
		}
	}

	fun collectAllPredecessors(mth: MethodNode, startBlock: BlockNode): List<BlockNode> {
		val list = ArrayList<BlockNode>(checkNotNull(mth.basicBlocks).size)
		val nextFunc: (BlockNode) -> List<BlockNode> = { b -> b.predecessors }
		visitDFS(mth, startBlock, nextFunc) { b -> list.add(b) }
		return list
	}

	fun collectAllSuccessors(mth: MethodNode, startBlock: BlockNode, clean: Boolean): List<BlockNode> {
		val list = ArrayList<BlockNode>(checkNotNull(mth.basicBlocks).size)
		val nextFunc: (BlockNode) -> List<BlockNode> = { b ->
			if (clean) cleanSuccessors(b) ?: emptyList() else b.successors
		}
		visitDFS(mth, startBlock, nextFunc) { b -> list.add(b) }
		return list
	}

	fun collectAllSuccessorsUntil(
		mth: MethodNode,
		startBlock: BlockNode,
		clean: Boolean,
		stopCondition: (BlockNode) -> Boolean,
	): List<BlockNode> {
		val blocks = ArrayList<BlockNode>()
		collectAllSuccessorsUntil(mth, blocks, startBlock, clean, stopCondition)
		return blocks
	}

	private fun collectAllSuccessorsUntil(
		mth: MethodNode,
		blocks: MutableList<BlockNode>,
		currentBlock: BlockNode,
		clean: Boolean,
		stopCondition: (BlockNode) -> Boolean,
	) {
		if (blocks.contains(currentBlock)) {
			return
		}
		blocks.add(currentBlock)
		if (stopCondition(currentBlock)) {
			return
		}
		val successors = if (clean) cleanSuccessors(currentBlock) else currentBlock.successors
		for (successor in successors) {
			collectAllSuccessorsUntil(mth, blocks, successor, clean, stopCondition)
		}
	}

	fun getBottomCommonPredecessor(mth: MethodNode, blocks: List<BlockNode>, containedBlocks: Set<BlockNode>): BlockNode? = getBottomCommonPredecessor(mth, blocks, containedBlocks, false)

	fun getBottomCommonPredecessor(
		mth: MethodNode,
		blocks: List<BlockNode>,
		containedBlocks: Set<BlockNode>,
		addTopBlock: Boolean,
	): BlockNode? {
		if (blocks.isEmpty()) {
			return null
		}
		val visitedPredecessorsByAll = HashSet(collectAllPredecessors(mth, blocks[0]))
		if (addTopBlock) {
			val topBlock = getBottomBlock(blocks)
			if (topBlock != null) {
				visitedPredecessorsByAll.add(topBlock)
			}
		}
		for (i in 1 until blocks.size) {
			val nextBlock = blocks[i]
			val predecessors = collectAllPredecessors(mth, nextBlock)
			visitedPredecessorsByAll.retainAll(predecessors)
		}
		return getBottomBlock(ArrayList(visitedPredecessorsByAll))
	}

	fun getTopCommonSuccessor(mth: MethodNode, blocks: List<BlockNode>, cleanOnly: Boolean): BlockNode? = getTopCommonSuccessor(mth, blocks, cleanOnly, false)

	fun getTopCommonSuccessor(
		mth: MethodNode,
		blocks: List<BlockNode>,
		cleanOnly: Boolean,
		addTopBlock: Boolean,
	): BlockNode? {
		if (blocks.isEmpty()) {
			return null
		}
		val visitedSuccessorsByAll = HashSet(collectAllSuccessors(mth, blocks[0], cleanOnly))
		if (addTopBlock) {
			val topBlock = getTopBlock(blocks)
			if (topBlock != null) {
				visitedSuccessorsByAll.add(topBlock)
			}
		}
		for (i in 1 until blocks.size) {
			val nextBlock = blocks[i]
			val successors = collectAllSuccessors(mth, nextBlock, cleanOnly)
			visitedSuccessorsByAll.retainAll(successors)
		}
		return getTopBlock(ArrayList(visitedSuccessorsByAll))
	}

	fun visitDFS(mth: MethodNode, visitor: (BlockNode) -> Unit) {
		visitDFS(mth, checkNotNull(mth.enterBlock), { b -> b.successors }, visitor)
	}

	fun visitReverseDFS(mth: MethodNode, visitor: (BlockNode) -> Unit) {
		visitDFS(mth, checkNotNull(mth.exitBlock), { b -> b.predecessors }, visitor)
	}

	private fun visitDFS(
		mth: MethodNode,
		startBlock: BlockNode,
		nextFunc: (BlockNode) -> List<BlockNode>,
		visitor: (BlockNode) -> Unit,
	) {
		val dfsIteration = DFSIteration(mth, startBlock, nextFunc)
		while (true) {
			val next = dfsIteration.next() ?: return
			visitor(next)
		}
	}

	fun collectPredecessors(mth: MethodNode, start: BlockNode, stopBlocks: Collection<BlockNode>): List<BlockNode> {
		val bs = newBlocksBitSet(mth)
		if (stopBlocks.isNotEmpty()) {
			bs.or(blocksToBitSet(mth, stopBlocks))
		}
		val list = ArrayList<BlockNode>()
		traversePredecessors(start, bs) { block ->
			list.add(block)
			false
		}
		return list
	}

	fun visitPredecessorsUntil(mth: MethodNode, start: BlockNode, visitor: (BlockNode) -> Boolean) {
		traversePredecessors(start, newBlocksBitSet(mth), visitor)
	}

	/**
	 * 向上 BFS；visitor 返回 true 时停止。
	 */
	private fun traversePredecessors(start: BlockNode, visited: BitSet, visitor: (BlockNode) -> Boolean) {
		val queue = ArrayDeque<BlockNode>()
		queue.add(start)
		while (true) {
			val current = queue.poll()
			if (current == null || visitor(current)) {
				return
			}
			for (next in current.predecessors) {
				val id = next.id
				if (!visited.get(id)) {
					visited.set(id)
					queue.add(next)
				}
			}
		}
	}

	/**
	 * 收集从 start 到 end 所有可能执行路径上的块。
	 */
	fun getAllPathsBlocks(start: BlockNode, end: BlockNode): Set<BlockNode> {
		val set: MutableSet<BlockNode> = HashSet()
		set.add(start)
		if (start !== end) {
			addPredecessors(set, end, start)
		}
		return set
	}

	/**
	 * 收集从 start 到 end 的一条不含指令的执行路径上的块。
	 */
	fun getOneEmptyPath(start: BlockNode, end: BlockNode): List<BlockNode>? = collectPathUntil(start, end, false) { b -> b.instructions.isEmpty() || b === end }

	/**
	 * 收集从 start 到 end 的一条可能执行路径上的块。
	 */
	fun getOnePath(start: BlockNode, end: BlockNode): List<BlockNode>? = collectPathUntil(start, end, false) { true }

	private fun addPredecessors(set: MutableSet<BlockNode>, from: BlockNode, until: BlockNode) {
		set.add(from)
		for (pred in from.predecessors) {
			if (pred !== until && !set.contains(pred)) {
				addPredecessors(set, pred, until)
			}
		}
	}

	private fun traverseSuccessorsUntil(from: BlockNode, until: BlockNode, visited: BitSet, clean: Boolean): Boolean = traverseSuccessorsUntil(from, until, visited, clean) { true }

	/**
	 * 遍历后继直到遇到目标节点。
	 *
	 * @param pred 只有满足该谓词的块才会被继续探索（until 或其可达支配者必须满足 pred）
	 */
	private fun traverseSuccessorsUntil(
		from: BlockNode,
		until: BlockNode,
		visited: BitSet,
		clean: Boolean,
		pred: (BlockNode) -> Boolean,
	): Boolean {
		val nodes = if (clean) cleanSuccessors(from) else from.successors
		for (s in nodes) {
			if (!pred(s)) {
				continue
			}
			if (s === until) {
				return true
			}
			if (s === from) {
				// 忽略块自环
				continue
			}
			val id = s.pos
			if (!visited.get(id)) {
				visited.set(id)
				if (until.isDominator(s)) {
					return true
				}
				if (traverseSuccessorsUntil(s, until, visited, clean, pred)) {
					return true
				}
			}
		}
		return false
	}

	/**
	 * 遍历后继直到遇到目标节点，并收集路径。
	 */
	fun collectPathUntil(from: BlockNode, until: BlockNode, clean: Boolean, pred: (BlockNode) -> Boolean): List<BlockNode>? {
		val path = internalCollectPathUntil(from, until, BitSet(), clean, pred) ?: return null
		path.add(from)
		Collections.reverse(path)
		return path
	}

	private fun internalCollectPathUntil(
		from: BlockNode,
		until: BlockNode,
		visited: BitSet,
		clean: Boolean,
		pred: (BlockNode) -> Boolean,
	): MutableList<BlockNode>? {
		val nodes = if (clean) cleanSuccessors(from) else from.successors
		for (s in nodes) {
			if (!pred(s)) {
				continue
			}
			if (s === until) {
				val path = ArrayList<BlockNode>()
				path.add(s)
				return path
			}
			val id = s.pos
			if (!visited.get(id)) {
				visited.set(id)
				val path = internalCollectPathUntil(s, until, visited, clean, pred)
				if (path != null) {
					path.add(s)
					return path
				}
			}
		}
		return null
	}

	/**
	 * 从 startBlocks 到 end 至少存在一条路径。
	 */
	fun atLeastOnePathExists(startBlocks: Collection<BlockNode>, end: BlockNode): Boolean {
		for (startBlock in startBlocks) {
			if (isPathExists(startBlock, end)) {
				return true
			}
		}
		return false
	}

	/**
	 * 从每个 startBlocks 到 end 都存在路径。
	 */
	fun isAllPathExists(startBlocks: Collection<BlockNode>, end: BlockNode): Boolean {
		for (startBlock in startBlocks) {
			if (!isPathExists(startBlock, end)) {
				return false
			}
		}
		return true
	}

	fun isPathExists(start: BlockNode, end: BlockNode): Boolean {
		if (start === end || cleanSuccessors(start).contains(end)) {
			return true
		}
		return traverseSuccessorsUntil(start, end, BitSet(), true)
	}

	fun isAnyPathExists(start: BlockNode, end: BlockNode): Boolean {
		if (start === end || end.isDominator(start) || start.successors.contains(end)) {
			return true
		}
		return traverseSuccessorsUntil(start, end, BitSet(), false)
	}

	fun isPathExists(start: BlockNode, end: BlockNode, pred: (BlockNode) -> Boolean): Boolean {
		if (start === end) {
			return true
		}
		return traverseSuccessorsUntil(start, end, BitSet(), false, pred)
	}

	fun getTopBlock(blocks: List<BlockNode>): BlockNode? {
		if (blocks.size == 1) {
			return blocks[0]
		}
		for (from in blocks) {
			var top = true
			for (to in blocks) {
				if (from !== to && !isAnyPathExists(from, to)) {
					top = false
					break
				}
			}
			if (top) {
				return from
			}
		}
		return null
	}

	/**
	 * 从输入集合中找出控制流图的最后一个块。
	 */
	fun getBottomBlock(blocks: List<BlockNode>): BlockNode? = getBottomBlock(blocks, false)

	fun getBottomBlock(blocks: List<BlockNode>, clean: Boolean): BlockNode? {
		if (blocks.size == 1) {
			return blocks[0]
		}
		// 尝试 1：找一个被其他所有块支配的块（clean 时不适用，因为支配关系考虑全部后继）
		if (!clean) {
			for (bottomCandidate in blocks) {
				var bottom = true
				for (from in blocks) {
					if (bottomCandidate !== from && !bottomCandidate.isDominator(from)) {
						bottom = false
						break
					}
				}
				if (bottom) {
					return bottomCandidate
				}
			}
		}
		// 尝试 2：找一个从其他所有块都能到达的块
		for (bottomCandidate in blocks) {
			var bottom = true
			for (from in blocks) {
				if (clean) {
					if (bottomCandidate !== from && !isPathExists(from, bottomCandidate)) {
						bottom = false
						break
					}
				} else {
					if (bottomCandidate !== from && !isAnyPathExists(from, bottomCandidate)) {
						bottom = false
						break
					}
				}
			}
			if (bottom) {
				return bottomCandidate
			}
		}
		return null
	}

	fun isOnlyOnePathExists(start: BlockNode, end: BlockNode): Boolean {
		if (start === end) {
			return true
		}
		if (!end.isDominator(start)) {
			return false
		}
		var currentNode = start
		while (cleanSuccessors(currentNode).size == 1) {
			currentNode = cleanSuccessors(currentNode)[0]
			if (currentNode === end) {
				return true
			}
		}
		return false
	}

	/**
	 * 从 start 出发，找第一个不被 dom 支配的节点。
	 */
	fun traverseWhileDominates(dom: BlockNode, start: BlockNode): BlockNode? {
		for (node in cleanSuccessors(start)) {
			if (!node.isDominator(dom)) {
				return node
			} else {
				val out = traverseWhileDominates(dom, node)
				if (out != null) {
					return out
				}
			}
		}
		return null
	}

	/**
	 * 在支配树中找输入集合的最低公共祖先。
	 */
	fun getCommonDominator(mth: MethodNode, blocks: List<BlockNode>): BlockNode? {
		val doms = newBlocksBitSet(mth)
		// 收集输入集合的全部支配者
		doms.set(0, checkNotNull(mth.basicBlocks).size)
		blocks.forEach { b -> doms.and(checkNotNull(b.doms)) }
		// 排除直接支配者的支配者（含自身）
		val combine = newBlocksBitSet(mth)
		combine.or(doms)
		forEachBlockFromBitSet(mth, doms) { block ->
			val idom = block.idom
			if (idom != null) {
				combine.andNot(checkNotNull(idom.doms))
				combine.clear(idom.id)
			}
		}
		return bitSetToOneBlock(mth, combine)
	}

	/**
	 * 返回一条边的支配边界：所有到目标块的路径都必须经过该边的块。
	 */
	fun getDomFrontierThroughEdge(edge: Edge): BitSet {
		val target = edge.target
		return if (target.predecessors.size > 1) {
			val dominanceFrontier = BitSet()
			dominanceFrontier.set(target.pos)
			dominanceFrontier
		} else {
			checkNotNull(target.domFrontier)
		}
	}

	/**
	 * 返回输入集合的公共交叉块。
	 *
	 * @return 可能是给定块之一；若交叉点是方法出口则返回 null
	 */
	fun getPathCross(mth: MethodNode, blocks: Collection<BlockNode>): BlockNode? {
		val domFrontBS = newBlocksBitSet(mth)
		val tmpBS = newBlocksBitSet(mth) // 保存块自身及其支配边界
		var first = true
		for (b in blocks) {
			tmpBS.clear()
			tmpBS.set(b.id)
			tmpBS.or(checkNotNull(b.domFrontier))
			if (first) {
				domFrontBS.or(tmpBS)
				first = false
			} else {
				domFrontBS.and(tmpBS)
			}
		}
		domFrontBS.clear(checkNotNull(mth.exitBlock).id)
		if (domFrontBS.isEmpty()) {
			return null
		}
		var oneBlock = bitSetToOneBlock(mth, domFrontBS)
		if (oneBlock != null) {
			return oneBlock
		}
		val excluded = newBlocksBitSet(mth)
		// 排除方法出口和循环起点
		excluded.set(checkNotNull(mth.exitBlock).id)
		mth.getLoops().forEach { l -> excluded.set(l.start.id) }
		if (!mth.isNoExceptionHandlers()) {
			// 排除异常处理路径
			mth.getExceptionHandlers().forEach { h -> addExcHandler(mth, h, excluded) }
		}
		domFrontBS.andNot(excluded)
		oneBlock = bitSetToOneBlock(mth, domFrontBS)
		if (oneBlock != null) {
			return oneBlock
		}
		val combinedDF = newBlocksBitSet(mth)
		var k = checkNotNull(mth.basicBlocks).size
		while (true) {
			// 不断合并支配边界，直到只剩一个块
			forEachBlockFromBitSet(mth, domFrontBS) { block ->
				val domFrontier = block.domFrontier
				if (domFrontier != null && !domFrontier.isEmpty()) {
					combinedDF.or(domFrontier)
				}
			}
			combinedDF.andNot(excluded)
			val cardinality = combinedDF.cardinality()
			if (cardinality == 1) {
				return bitSetToOneBlock(mth, combinedDF)
			}
			if (cardinality == 0) {
				return null
			}
			if (k-- < 0) {
				mth.addWarnComment(
					"Path cross not found for $blocks, limit reached: " + checkNotNull(mth.basicBlocks).size,
				)
				return null
			}
			domFrontBS.clear()
			domFrontBS.or(combinedDF)
			combinedDF.clear()
		}
	}

	private fun addExcHandler(mth: MethodNode, handler: ExceptionHandler, set: BitSet) {
		val handlerBlock = handler.getHandlerBlock()
		if (handlerBlock == null) {
			mth.addDebugComment("Null handler block in: $handler")
			return
		}
		set.set(handlerBlock.id)
	}

	fun getPathCross(mth: MethodNode, b1: BlockNode?, b2: BlockNode?): BlockNode? {
		if (b1 === b2) {
			return b1
		}
		if (b1 == null || b2 == null) {
			return null
		}
		return getPathCross(mth, Arrays.asList(b1, b2))
	}

	/**
	 * 从 start 开始，收集所有被 dominator 支配的块。
	 */
	fun collectBlocksDominatedBy(mth: MethodNode, dominator: BlockNode, start: BlockNode): List<BlockNode> {
		val result = ArrayList<BlockNode>()
		collectWhileDominates(dominator, start, result, newBlocksBitSet(mth), false)
		return result
	}

	/**
	 * 从 start 开始，收集所有被 dominator 支配的块（含异常处理）。
	 */
	fun collectBlocksDominatedByWithExcHandlers(mth: MethodNode, dominator: BlockNode, start: BlockNode): Set<BlockNode> {
		val result: MutableSet<BlockNode> = LinkedHashSet()
		collectWhileDominates(dominator, start, result, newBlocksBitSet(mth), true)
		return result
	}

	private fun collectWhileDominates(
		dominator: BlockNode,
		child: BlockNode,
		result: MutableCollection<BlockNode>,
		visited: BitSet,
		includeExcHandlers: Boolean,
	) {
		if (visited.get(child.id)) {
			return
		}
		visited.set(child.id)
		val successors = if (includeExcHandlers) child.successors else cleanSuccessors(child)
		for (node in successors) {
			if (node.isDominator(dominator)) {
				result.add(node)
				collectWhileDominates(dominator, node, result, visited, includeExcHandlers)
			}
		}
	}

	/**
	 * 访问一条没有分支/汇合的单路径上的块。
	 */
	fun visitSinglePath(startBlock: BlockNode?, visitor: (BlockNode) -> Unit) {
		if (startBlock == null) {
			return
		}
		visitor(startBlock)
		var next = getNextSinglePathBlock(startBlock)
		while (next != null) {
			visitor(next)
			next = getNextSinglePathBlock(next)
		}
	}

	fun getNextSinglePathBlock(block: BlockNode?): BlockNode? {
		if (block == null || block.predecessors.size > 1) {
			return null
		}
		val successors = block.successors
		return if (successors.size == 1) successors[0] else null
	}

	fun buildSimplePath(block: BlockNode?): List<BlockNode> {
		if (block == null) {
			return Collections.emptyList()
		}
		val list = ArrayList<BlockNode>()
		if (cleanSuccessors(block).size >= 2) {
			return Collections.emptyList()
		}
		list.add(block)
		var currentBlock = getNextBlock(block)
		while (currentBlock != null &&
			cleanSuccessors(currentBlock).size < 2 &&
			currentBlock.predecessors.size == 1
		) {
			list.add(currentBlock)
			currentBlock = getNextBlock(currentBlock)
		}
		return list
	}

	/**
	 * 从 start 块出发，给所有合成的空前驱块打上 SKIP 标记。
	 */
	fun skipPredSyntheticPaths(block: BlockNode) {
		for (pred in block.predecessors) {
			if (pred.contains(AFlag.SYNTHETIC) &&
				!pred.contains(AFlag.EXC_TOP_SPLITTER) &&
				!pred.contains(AFlag.EXC_BOTTOM_SPLITTER) &&
				pred.instructions.isEmpty()
			) {
				pred.add(AFlag.DONT_GENERATE)
				skipPredSyntheticPaths(pred)
			}
		}
	}

	/**
	 * 沿空路径前进，返回路径末端（第一个非空块）；无此路径则返回起始块。
	 */
	fun followEmptyPath(start: BlockNode): BlockNode = followEmptyPath(start, false)

	fun followEmptyPath(start: BlockNode, reverse: Boolean): BlockNode = followEmptyPath(start, reverse, true)

	fun followEmptyPath(start0: BlockNode, reverse: Boolean, cleanOnly: Boolean): BlockNode {
		var start = start0
		while (true) {
			val next = getNextBlockOnEmptyPath(start, reverse, cleanOnly) ?: return start
			start = next
		}
	}

	fun followEmptyUpPathWithinSet(start: BlockNode, traversableBlocks: Collection<BlockNode>): List<BlockNode> {
		val results = LinkedList<BlockNode>()
		followEmptyUpPathWithinSet(results, start, traversableBlocks, HashSet())
		return results
	}

	fun followEmptyUpPathWithinSet(
		results: MutableList<BlockNode>,
		start0: BlockNode,
		traversableBlocks: Collection<BlockNode>,
		traversedBlocks: MutableCollection<BlockNode>,
	) {
		var start = start0
		val predecessors = ListUtils.filter(start.predecessors) { traversableBlocks.contains(it) }
		for (predecessor in predecessors) {
			if (!traversableBlocks.contains(predecessor) || traversedBlocks.contains(predecessor)) {
				continue
			}
			traversedBlocks.add(predecessor)
			if (predecessor.instructions.isEmpty()) {
				followEmptyUpPathWithinSet(results, start, traversableBlocks, traversedBlocks)
			} else {
				results.add(predecessor)
			}
			start = predecessor
		}
	}

	fun visitBlocksOnEmptyPath(start: BlockNode, visitor: (BlockNode) -> Unit) {
		visitBlocksOnEmptyPath(start, visitor, false)
	}

	fun visitBlocksOnEmptyPath(start0: BlockNode, visitor: (BlockNode) -> Unit, reverse: Boolean) {
		var start = start0
		while (true) {
			val next = getNextBlockOnEmptyPath(start, reverse) ?: return
			visitor(next)
			start = next
		}
	}

	private fun getNextBlockOnEmptyPath(block: BlockNode): BlockNode? = getNextBlockOnEmptyPath(block, false)

	private fun getNextBlockOnEmptyPath(block: BlockNode, reverse: Boolean): BlockNode? = getNextBlockOnEmptyPath(block, reverse, true)

	private fun getNextBlockOnEmptyPath(block: BlockNode, reverse: Boolean, cleanOnly: Boolean): BlockNode? {
		if (!block.instructions.isEmpty() ||
			(!reverse && block.predecessors.size > 1) ||
			(reverse && cleanSuccessors(block).size > 1)
		) {
			return null
		}
		val nextBlocks =
			if (reverse) block.predecessors else (if (cleanOnly) cleanSuccessors(block) else block.successors)
		if (nextBlocks.size != 1) {
			return null
		}
		return nextBlocks[0]
	}

	/**
	 * 从 start 到 end 的路径上没有指令、没有分支时返回 true。
	 */
	fun isEmptySimplePath(start: BlockNode, end: BlockNode): Boolean {
		if (start === end && start.instructions.isEmpty()) {
			return true
		}
		if (!start.instructions.isEmpty() || cleanSuccessors(start).size != 1) {
			return false
		}
		var block = getNextBlock(start)
		while (block != null &&
			block !== end &&
			cleanSuccessors(block).size < 2 &&
			block.predecessors.size == 1 &&
			block.instructions.isEmpty()
		) {
			block = getNextBlock(block)
		}
		return block === end
	}

	/**
	 * 返回合成块的前驱（或原块）。
	 */
	fun skipSyntheticPredecessor(block: BlockNode): BlockNode {
		if (block.isSynthetic && block.instructions.isEmpty() && block.predecessors.size == 1) {
			return block.predecessors[0]
		}
		return block
	}

	fun isAllBlocksEmpty(blocks: List<BlockNode>?): Boolean {
		if (blocks == null || blocks.isEmpty()) {
			return true
		}
		for (block in blocks) {
			if (!block.instructions.isEmpty()) {
				return false
			}
		}
		return true
	}

	fun collectAllInsns(blocks: List<BlockNode>): List<InsnNode> {
		val insns = ArrayList<InsnNode>()
		blocks.forEach { block -> insns.addAll(block.instructions) }
		return insns
	}

	/**
	 * 返回方法中有限数量的指令；超过上限时返回空列表。
	 */
	fun collectInsnsWithLimit(blocks: List<BlockNode>, limit: Int): List<InsnNode> {
		val insns = ArrayList<InsnNode>(limit)
		for (block in blocks) {
			val blockInsns = block.instructions
			val blockSize = blockInsns.size
			if (blockSize == 0) {
				continue
			}
			if (insns.size + blockSize > limit) {
				return Collections.emptyList()
			}
			insns.addAll(blockInsns)
		}
		return insns
	}

	/**
	 * 若方法中只有一条指令则返回它，否则返回 null。
	 */
	fun getOnlyOneInsnFromMth(mth: MethodNode): InsnNode? {
		if (mth.isNoCode()) {
			return null
		}
		var insn: InsnNode? = null
		for (block in checkNotNull(mth.basicBlocks)) {
			val blockInsns = block.instructions
			val blockSize = blockInsns.size
			if (blockSize == 0) {
				continue
			}
			if (blockSize > 1) {
				return null
			}
			if (insn != null) {
				return null
			}
			insn = blockInsns[0]
		}
		return insn
	}

	fun isFirstInsn(mth: MethodNode, insn: InsnNode): Boolean {
		val startBlock = followEmptyPath(checkNotNull(mth.enterBlock))
		if (startBlock.instructions.isNotEmpty()) {
			return startBlock.instructions[0] === insn
		}
		// 处理带空块的分支
		val block = getBlockByInsn(mth, insn) ?: throw JadxRuntimeException("Insn not found in method: $insn")
		if (block.instructions[0] !== insn) {
			return false
		}
		val allPathsBlocks = getAllPathsBlocks(checkNotNull(mth.enterBlock), block)
		for (pathBlock in allPathsBlocks) {
			if (!pathBlock.instructions.isEmpty() && pathBlock !== block) {
				return false
			}
		}
		return true
	}

	/**
	 * 用第 i 条指令替换块中的指令，并保留属性/元数据。
	 */
	fun replaceInsn(mth: MethodNode, block: BlockNode, i: Int, insn: InsnNode) {
		val prevInsn = block.instructions[i]
		insn.copyAttributesFrom(prevInsn)
		insn.inheritMetadata(prevInsn)
		insn.setOffset(prevInsn.getOffset())
		block.instructions[i] = insn

		val result = insn.result
		val prevResult = prevInsn.result
		if (result != null && prevResult != null && result.sameRegAndSVar(prevResult)) {
			// 同一寄存器不解绑结果（解绑会从 PHI 移除且不会重新加回）
			InsnRemover.unbindAllArgs(mth, prevInsn)
		} else {
			InsnRemover.unbindInsn(mth, prevInsn)
		}
		insn.rebindArgs()
	}

	fun replaceInsn(mth: MethodNode, block: BlockNode, oldInsn: InsnNode, newInsn: InsnNode): Boolean {
		val instructions = block.instructions
		val size = instructions.size
		for (i in 0 until size) {
			if (instructions[i] === oldInsn) {
				replaceInsn(mth, block, i, newInsn)
				return true
			}
		}
		return false
	}

	fun removeInstructions(blocks: List<IBlock>) {
		for (block in blocks) {
			(block.instructions as MutableList<InsnNode>).clear()
		}
	}

	fun insertBeforeInsn(block: BlockNode, insn: InsnNode, newInsn: InsnNode): Boolean {
		val index = getInsnIndexInBlock(block, insn)
		if (index == -1) {
			return false
		}
		block.instructions.add(index, newInsn)
		return true
	}

	fun insertAfterInsn(block: BlockNode, insn: InsnNode, newInsn: InsnNode): Boolean {
		val index = getInsnIndexInBlock(block, insn)
		if (index == -1) {
			return false
		}
		block.instructions.add(index + 1, newInsn)
		return true
	}

	fun getInsnIndexInBlock(block: BlockNode, insn: InsnNode): Int {
		val instructions = block.instructions
		val size = instructions.size
		for (i in 0 until size) {
			if (instructions[i] === insn) {
				return i
			}
		}
		return -1
	}

	fun replaceInsn(mth: MethodNode, oldInsn: InsnNode, newInsn: InsnNode): Boolean {
		for (block in checkNotNull(mth.basicBlocks)) {
			if (replaceInsn(mth, block, oldInsn, newInsn)) {
				return true
			}
		}
		return false
	}

	fun getTopSplitterForHandler(handlerBlock: BlockNode): BlockNode {
		val block = getBlockWithFlag(handlerBlock.predecessors, AFlag.EXC_TOP_SPLITTER)
		if (block == null) {
			throw JadxRuntimeException("Can't find top splitter block for handler:$handlerBlock")
		}
		return block
	}

	/**
	 * 返回 try/catch 的出口块：从 handler 块沿支配边界找到 try 分支与 catch 分支汇合的第一个边界。
	 */
	fun getTryAndHandlerCrossBlock(mth: MethodNode, handler: ExceptionHandler): BlockNode? {
		val start = checkNotNull(handler.getHandlerBlock())
		val topSplitter = getTopSplitterForHandler(start)
		val allHandlers = checkNotNull(handler.getTryBlock()).handlers
		val handlerExitsCandidate = ArrayList(bitSetToBlocks(mth, start.domFrontier))
		val visited = newBlocksBitSet(mth)
		while (handlerExitsCandidate.isNotEmpty()) {
			val frontier = handlerExitsCandidate.removeAt(0)
			if (visited.get(frontier.pos)) {
				continue
			}
			visited.set(frontier.pos)
			// 确认 frontier 的前驱来自 try 分支末尾，而非 handler 分支
			for (pred in frontier.predecessors) {
				val predFromHandler = allHandlers.any { h -> isPathExists(checkNotNull(h.getHandlerBlock()), pred) }
				if (!predFromHandler && isPathExists(topSplitter, pred) && frontier !== mth.exitBlock) {
					return frontier
				}
			}
			handlerExitsCandidate.addAll(bitSetToBlocks(mth, frontier.domFrontier))
		}
		return null
	}

	fun getBlockWithFlag(blocks: List<BlockNode>, flag: AFlag): BlockNode? {
		for (block in blocks) {
			if (block.contains(flag)) {
				return block
			}
		}
		return null
	}

	fun getCatchAttrForInsn(mth: MethodNode, insn: InsnNode): CatchAttr? {
		val catchAttr = insn.get(AType.EXC_CATCH)
		if (catchAttr != null) {
			return catchAttr
		}
		val block = getBlockByInsn(mth, insn) ?: return null
		return block.get(AType.EXC_CATCH)
	}

	fun isEqualPaths(b1: BlockNode?, b2: BlockNode?): Boolean {
		if (b1 === b2) {
			return true
		}
		if (b1 == null || b2 == null) {
			return false
		}
		return isEqualReturnBlocks(b1, b2) || isEmptySyntheticPath(b1, b2) || isDuplicateBlockPath(b1, b2)
	}

	private fun isEmptySyntheticPath(b1: BlockNode, b2: BlockNode): Boolean {
		val n1 = followEmptyPath(b1)
		val n2 = followEmptyPath(b2)
		return n1 === n2 || isEqualReturnBlocks(n1, n2)
	}

	fun isEqualReturnBlocks(b1: BlockNode, b2: BlockNode): Boolean {
		if (!b1.isReturnBlock || !b2.isReturnBlock) {
			return false
		}
		val b1Insns = b1.instructions
		val b2Insns = b2.instructions
		if (b1Insns.size != 1 || b2Insns.size != 1) {
			return false
		}
		val i1 = b1Insns[0]
		val i2 = b2Insns[0]
		if (i1.argsCount != i2.argsCount) {
			return false
		}
		if (i1.argsCount == 0) {
			return true
		}
		val firstArg = i1.getArg(0)
		val secondArg = i2.getArg(0)
		if (firstArg.isSameConst(secondArg)) {
			return true
		}
		if (i1.sourceLine != i2.sourceLine) {
			return false
		}
		return firstArg == secondArg
	}

	fun isDuplicateBlockPath(first: BlockNode, second: BlockNode): Boolean {
		if (first.successors.size == 1 && second.successors.size == 1 &&
			first.successors[0] == second.successors[0]
		) {
			return isSameInsnsBlocks(first, second)
		}
		return false
	}

	fun isSameInsnsBlocks(first: BlockNode, second: BlockNode): Boolean {
		val firstInsns = first.instructions
		val secondInsns = second.instructions
		if (firstInsns.size != secondInsns.size) {
			return false
		}
		val len = firstInsns.size
		for (i in 0 until len) {
			if (!isInsnDeepEquals(firstInsns[i], secondInsns[i])) {
				return false
			}
		}
		return true
	}

	private fun isInsnDeepEquals(first: InsnNode, second: InsnNode): Boolean {
		if (first === second) {
			return true
		}
		return first.isSame(second) &&
			Objects.equals(first.getArguments(), second.getArguments()) &&
			resultIsSameReg(first.result, second.result)
	}

	private fun resultIsSameReg(first: RegisterArg?, second: RegisterArg?): Boolean {
		if (first == null || second == null) {
			return first === second
		}
		return first.regNum == second.regNum
	}
}
