package jadx.core.utils

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.AttrList
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.attributes.nodes.LoopLabelAttr
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IBranchRegion
import jadx.core.dex.nodes.IConditionRegion
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.SwitchRegion
import jadx.core.dex.regions.TryCatchRegion
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.dex.trycatch.TryCatchBlockAttr
import jadx.core.dex.visitors.regions.AbstractRegionVisitor
import jadx.core.dex.visitors.regions.DepthRegionTraversal
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.Collections

/**
 * 区域（Region）遍历与判断工具集。
 *
 * **用途**：在 CFG 的区域树（[IRegion]/[IBlock] 组成的树）上做递归查询：
 * 找首/末指令、判断是否有退出路径、判断块是否属于区域等。
 *
 * **Kotlin 转换说明**：全部为静态方法，用 `object` + `@JvmStatic`。
 */
object RegionUtils {

	fun hasExitEdge(container: IContainer): Boolean {
		if (container is IBlock) {
			return BlockUtils.containsExitInsn(container)
		}
		if (container is IBranchRegion) {
			// 所有分支都必须有退出边
			for (br in container.getBranches()) {
				if (br == null || !hasExitEdge(br)) {
					return false
				}
			}
			return true
		}
		if (container is IRegion) {
			val last = Utils.last(container.getSubBlocks())
			return last != null && hasExitEdge(last)
		}
		throw JadxRuntimeException(unknownContainerType(container))
	}

	fun getFirstInsn(container: IContainer): InsnNode? {
		if (container is IBlock) {
			val insnList = container.getInstructions()
			if (insnList.isEmpty()) {
				return null
			}
			return insnList[0]
		} else if (container is IBranchRegion) {
			return null
		} else if (container is IRegion) {
			val blocks = container.getSubBlocks()
			if (blocks.isEmpty()) {
				return null
			}
			return getFirstInsn(blocks[0])
		} else {
			throw JadxRuntimeException(unknownContainerType(container))
		}
	}

	fun getFirstBlock(container: IContainer?): IBlock? {
		if (container == null) {
			return null
		}
		if (container is IBlock) {
			return container
		} else if (container is IBranchRegion) {
			return null
		} else if (container is IRegion) {
			val blocks = container.getSubBlocks()
			if (blocks.isEmpty()) {
				return null
			}
			return getFirstBlock(blocks[0])
		} else {
			throw JadxRuntimeException(unknownContainerType(container))
		}
	}

	fun getFirstBlockNode(container: IContainer): BlockNode? {
		if (container is IBlock) {
			if (container is BlockNode) {
				return container
			}
			return null
		}
		if (container is IConditionRegion) {
			return ListUtils.firstOrNull(container.getConditionBlocks())
		}
		if (container is TryCatchRegion) {
			return getFirstBlockNode(container.tryRegion)
		}
		if (container is SwitchRegion) {
			return container.header
		}
		if (container is IRegion) {
			return getFirstBlockNode(container.getSubBlocks())
		}
		throw JadxRuntimeException(unknownContainerType(container))
	}

	private fun getFirstBlockNode(containers: List<IContainer>): BlockNode? {
		for (cont in containers) {
			val firstBlockNode = getFirstBlockNode(cont)
			if (firstBlockNode != null) {
				return firstBlockNode
			}
		}
		return null
	}

	fun getFirstSourceLine(container: IContainer): Int {
		if (container is IBlock) {
			return BlockUtils.getFirstSourceLine(container)
		}
		if (container is IConditionRegion) {
			return container.getConditionSourceLine()
		}
		if (container is IBranchRegion) {
			return getFirstSourceLine(container.getBranches())
		}
		if (container is IRegion) {
			return getFirstSourceLine(container.getSubBlocks())
		}
		return 0
	}

	private fun getFirstSourceLine(containers: List<IContainer?>): Int {
		if (containers.isEmpty()) {
			return 0
		}
		for (container in containers) {
			if (container != null) {
				val line = getFirstSourceLine(container)
				if (line != 0) {
					return line
				}
			}
		}
		return 0
	}

	fun getLastInsn(container: IContainer): InsnNode? {
		if (container is IBlock) {
			val insnList = container.getInstructions()
			if (insnList.isEmpty()) {
				return null
			}
			return insnList[insnList.size - 1]
		} else if (container is IBranchRegion) {
			return null
		} else if (container is IRegion) {
			val blocks = container.getSubBlocks()
			if (blocks.isEmpty()) {
				return null
			}
			return getLastInsn(blocks[blocks.size - 1])
		} else {
			throw JadxRuntimeException(unknownContainerType(container))
		}
	}

	fun getLastInsnWithBlock(container: IContainer): BlockInsnPair? {
		if (container is IBlock) {
			val lastInsn = ListUtils.last(container.getInstructions())
			if (lastInsn == null) {
				return null
			}
			return BlockInsnPair(container, lastInsn)
		}
		if (container is IBranchRegion) {
			val branches = container.getBranches()
			val count = branches.count { it != null }
			if (count == 1) {
				// 只有一个非空分支
				for (branch in branches) {
					if (branch != null) {
						return getLastInsnWithBlock(branch)
					}
				}
			}
			// 多个不同的最后指令
			return null
		}
		if (container is IRegion) {
			val blocks = container.getSubBlocks()
			if (blocks.isEmpty()) {
				return null
			}
			return getLastInsnWithBlock(checkNotNull(ListUtils.last(blocks)))
		}
		throw JadxRuntimeException(unknownContainerType(container))
	}

	fun getLastBlock(container: IContainer): IBlock? {
		if (container is IBlock) {
			return container
		} else if (container is IBranchRegion) {
			return null
		} else if (container is IRegion) {
			val blocks = container.getSubBlocks()
			if (blocks.isEmpty()) {
				return null
			}
			return getLastBlock(blocks[blocks.size - 1])
		} else {
			throw JadxRuntimeException(unknownContainerType(container))
		}
	}

	fun isExitBlock(mth: MethodNode, container: IContainer): Boolean {
		if (container is BlockNode) {
			return BlockUtils.isExitBlock(mth, container)
		}
		return false
	}

	/**
	 * 区域内最后一个块没有后继、或跳出了区域（return/break）时返回 true。
	 */
	fun hasExitBlock(container: IContainer?): Boolean {
		if (container == null) {
			return false
		}
		return hasExitBlock(container, container)
	}

	private fun hasExitBlock(rootContainer: IContainer, container: IContainer): Boolean {
		if (container is BlockNode) {
			if (BlockUtils.isExitBlock(container)) {
				return true
			}
			return isInsnExitContainer(rootContainer, container)
		}
		if (container is IBranchRegion) {
			return ListUtils.allMatch(container.getBranches()) { b -> hasExitBlock(b) }
		}
		if (container is IBlock) {
			return isInsnExitContainer(rootContainer, container)
		}
		if (container is IRegion) {
			val blocks = container.getSubBlocks()
			return blocks.isNotEmpty() && hasExitBlock(rootContainer, blocks[blocks.size - 1])
		}
		throw JadxRuntimeException(unknownContainerType(container))
	}

	private fun isInsnExitContainer(rootContainer: IContainer, block: IBlock): Boolean {
		val lastInsn = BlockUtils.getLastInsn(block) ?: return false
		val insnType = lastInsn.type
		if (insnType == InsnType.RETURN) {
			return true
		}
		if (insnType == InsnType.THROW) {
			// 检查 throw 之后是否还能在当前容器内继续执行（有匹配的 handler）
			val catchAttr = lastInsn.get(AType.EXC_CATCH)
			if (catchAttr != null) {
				for (handler in catchAttr.handlers) {
					if (isRegionContainsBlock(rootContainer, handler.getHandlerBlock())) {
						return false
					}
				}
			}
			return true
		}
		if (insnType == InsnType.BREAK) {
			val loopInfoAttrList: AttrList<LoopInfo>? = lastInsn.get(AType.LOOP)
			if (loopInfoAttrList != null) {
				for (loopInfo in loopInfoAttrList.list) {
					if (!isRegionContainsBlock(rootContainer, loopInfo.start)) {
						return true
					}
				}
			}
			val loopLabelAttr: LoopLabelAttr? = lastInsn.get(AType.LOOP_LABEL)
			if (loopLabelAttr != null &&
				!isRegionContainsBlock(rootContainer, loopLabelAttr.loop.start)
			) {
				return true
			}
		}
		return false
	}

	fun hasBreakInsn(container: IContainer): Boolean {
		if (container is IBlock) {
			return BlockUtils.checkLastInsnType(container, InsnType.BREAK)
		} else if (container is IRegion) {
			val blocks = container.getSubBlocks()
			return blocks.isNotEmpty() && hasBreakInsn(blocks[blocks.size - 1])
		} else {
			throw JadxRuntimeException("Unknown container type: $container")
		}
	}

	fun insnsCount(container: IContainer): Int {
		if (container is IBlock) {
			val insnList = container.getInstructions()
			var count = 0
			for (insn in insnList) {
				if (insn.contains(AFlag.DONT_GENERATE)) {
					continue
				}
				count++
			}
			return count
		}
		if (container is IRegion) {
			var count = 0
			for (block in container.getSubBlocks()) {
				count += insnsCount(block)
			}
			return count
		}
		throw JadxRuntimeException(unknownContainerType(container))
	}

	fun collectInsns(mth: MethodNode, container: IContainer): List<InsnNode> {
		val list = ArrayList<InsnNode>()
		visitBlocks(mth, container) { block -> list.addAll(block.getInstructions()) }
		return list
	}

	fun isEmpty(container: IContainer?): Boolean = !notEmpty(container)

	fun notEmpty(container: IContainer?): Boolean {
		if (container == null) {
			return false
		}
		if (container is IBlock) {
			val insnList = container.getInstructions()
			for (insnNode in insnList) {
				if (!insnNode.contains(AFlag.DONT_GENERATE)) {
					return true
				}
			}
			return false
		}
		if (container is LoopRegion) {
			return true
		}
		if (container is IRegion) {
			for (block in container.getSubBlocks()) {
				if (notEmpty(block)) {
					return true
				}
			}
			return false
		}
		throw JadxRuntimeException(unknownContainerType(container))
	}

	fun getAllRegionBlocks(container: IContainer, blocks: MutableSet<IBlock>) {
		if (container is IBlock) {
			blocks.add(container)
		} else if (container is IRegion) {
			for (block in container.getSubBlocks()) {
				getAllRegionBlocks(block, blocks)
			}
		} else {
			throw JadxRuntimeException(unknownContainerType(container))
		}
	}

	fun isRegionContainsBlock(container: IContainer, block: BlockNode?): Boolean {
		if (container is IBlock) {
			return container === block
		} else if (container is IRegion) {
			for (b in container.getSubBlocks()) {
				if (isRegionContainsBlock(b, block)) {
					return true
				}
			}
			return false
		} else {
			throw JadxRuntimeException(unknownContainerType(container))
		}
	}

	fun getSingleSubBlock(container: IContainer): IContainer? {
		if (container is Region) {
			val subBlocks = container.getSubBlocks()
			if (subBlocks.size == 1) {
				return ignoreSimpleRegionWrapper(subBlocks[0])
			}
		}
		return null
	}

	private fun ignoreSimpleRegionWrapper(container0: IContainer): IContainer {
		var container = container0
		while (true) {
			if (container is Region) {
				val subBlocks = container.getSubBlocks()
				if (subBlocks.size != 1) {
					return container
				}
				container = subBlocks[0]
			} else {
				return container
			}
		}
	}

	fun getExcHandlersForRegion(region: IContainer): List<IContainer> {
		val tb: TryCatchBlockAttr? = region.get(AType.TRY_BLOCK)
		if (tb != null) {
			val list = ArrayList<IContainer>(tb.handlersCount)
			for (eh in tb.handlers) {
				list.add(checkNotNull(eh.getHandlerRegion()))
			}
			return list
		}
		return Collections.emptyList()
	}

	fun getLoopsStartInRegion(mth: MethodNode, r: IRegion): List<LoopInfo> {
		val loops = ArrayList<LoopInfo>()
		visitBlocks(mth, r) { b ->
			if (b.contains(AFlag.LOOP_START)) {
				loops.addAll(b.getAll(AType.LOOP))
			}
		}
		return loops
	}

	private fun isRegionContainsExcHandlerRegion(container: IContainer, region: IRegion): Boolean {
		if (container === region) {
			return true
		}
		if (container is IRegion) {
			// 遍历子块
			for (b in container.getSubBlocks()) {
				// 处理 try 块
				val tb: TryCatchBlockAttr? = b.get(AType.TRY_BLOCK)
				if (tb != null && b is IRegion) {
					for (eh in tb.handlers) {
						if (isRegionContainsRegion(checkNotNull(eh.getHandlerRegion()), region)) {
							return true
						}
					}
				}
				if (isRegionContainsRegion(b, region)) {
					return true
				}
			}
		}
		return false
	}

	/**
	 * 判断 [region] 是否包含在 [container] 中。
	 *
	 * 简单区域（非异常处理区域）只需向上查父链；异常处理区域可能有多个父节点，
	 * 因此需要递归查找。
	 */
	fun isRegionContainsRegion(container: IContainer, region0: IRegion?): Boolean {
		if (container === region0) {
			return true
		}
		if (region0 == null) {
			return false
		}
		var region: IRegion = region0
		var parent = region.parent
		while (container !== parent) {
			if (parent == null) {
				if (region.contains(AType.EXC_HANDLER)) {
					return isRegionContainsExcHandlerRegion(container, region)
				}
				return false
			}
			region = parent
			parent = region.parent
		}
		return true
	}

	fun getBlockContainer(container: IContainer, block: IBlock): IContainer? {
		if (container is IBlock) {
			return if (container === block) container else null
		}
		if (container is IRegion) {
			for (c in container.getSubBlocks()) {
				val res = getBlockContainer(c, block)
				if (res != null) {
					return if (res is IBlock) container else res
				}
			}
			return null
		}
		throw JadxRuntimeException(unknownContainerType(container))
	}

	/**
	 * 判断两个块是否在同一层的同一区域内。
	 */
	fun isBlocksInSameRegion(mth: MethodNode, firstBlock: BlockNode, secondBlock: BlockNode): Boolean {
		val region = mth.region ?: return false
		val firstContainer = getBlockContainer(region, firstBlock)
		if (firstContainer is IRegion) {
			if (firstContainer is IBranchRegion) {
				return false
			}
			val subBlocks = firstContainer.getSubBlocks()
			return subBlocks.contains(secondBlock)
		}
		return false
	}

	fun isDominatedBy(dom: BlockNode, cont: IContainer): Boolean {
		if (dom === cont) {
			return true
		}
		if (cont is BlockNode) {
			return cont.isDominator(dom)
		} else if (cont is IBlock) {
			return false
		} else if (cont is IRegion) {
			for (c in cont.getSubBlocks()) {
				if (!isDominatedBy(dom, c)) {
					return false
				}
			}
			return true
		} else {
			throw JadxRuntimeException(unknownContainerType(cont))
		}
	}

	fun hasPathThroughBlock(block: BlockNode, cont: IContainer): Boolean {
		if (block === cont) {
			return true
		}
		if (cont is BlockNode) {
			return BlockUtils.isPathExists(block, cont)
		}
		if (cont is IBlock) {
			return false
		}
		if (cont is IRegion) {
			for (c in cont.getSubBlocks()) {
				if (hasPathThroughBlock(block, c)) {
					return true
				}
			}
			return false
		}
		throw JadxRuntimeException(unknownContainerType(cont))
	}

	/**
	 * 判断从 block 到 container 起点是否存在路径；block 在 container 内部时返回 false。
	 */
	fun isPathExists(block: BlockNode, container: IContainer): Boolean {
		val firstBlock = getFirstBlockNode(container)
		if (firstBlock != null) {
			return BlockUtils.isPathExists(block, firstBlock)
		}
		return false
	}

	fun unknownContainerType(container: IContainer?): String {
		if (container == null) {
			return "Null container variable"
		}
		return "Unknown container type: " + container.javaClass
	}

	fun visitBlocks(mth: MethodNode, container: IContainer, visitor: (IBlock) -> Unit) {
		DepthRegionTraversal.traverse(
			mth,
			container,
			object : AbstractRegionVisitor() {
				override fun processBlock(mth: MethodNode, block: IBlock) {
					visitor(block)
				}
			},
		)
	}

	fun visitBlockNodes(mth: MethodNode, container: IContainer, visitor: (BlockNode) -> Unit) {
		DepthRegionTraversal.traverse(
			mth,
			container,
			object : AbstractRegionVisitor() {
				override fun processBlock(mth: MethodNode, block: IBlock) {
					if (block is BlockNode) {
						visitor(block)
					}
				}
			},
		)
	}

	fun visitRegions(mth: MethodNode, container: IContainer, visitor: (IRegion) -> Boolean) {
		DepthRegionTraversal.traverse(
			mth,
			container,
			object : AbstractRegionVisitor() {
				override fun enterRegion(mth: MethodNode, region: IRegion): Boolean = visitor(region)
			},
		)
	}

	fun getNextContainer(mth: MethodNode, region: IRegion): IContainer? {
		val parent = checkNotNull(region.parent)
		val subBlocks = parent.getSubBlocks()
		val index = subBlocks.indexOf(region)
		if (index == -1 || index + 1 >= subBlocks.size) {
			return null
		}
		return subBlocks[index + 1]
	}

	/** 给区域内所有块打上指定标记。 */
	fun addToAll(mth: MethodNode, container: IContainer, flag: AFlag) {
		visitBlocks(mth, container) { t -> t.add(flag) }
	}
}
