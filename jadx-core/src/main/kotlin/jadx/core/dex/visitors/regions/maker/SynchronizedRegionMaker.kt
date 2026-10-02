package jadx.core.dex.visitors.regions.maker

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.ConstClassNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.SynchronizedRegion
import jadx.core.dex.visitors.regions.CleanRegions
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnRemover
import jadx.core.utils.Utils

/**
 * 把 `monitor-enter` / `monitor-exit` 指令对还原成 `synchronized` 区域。
 *
 * **算法意图**：synchronized 在字节码里是 MONITOR_ENTER 进入、若干 MONITOR_EXIT 退出。
 * 本类从进入块出发，沿后继搜索所有 MONITOR_EXIT 块作为退出集合，再确定
 * synchronized 体与整个块的出口；[removeSynchronized] 则用于 `synchronized` 方法
 * （整个方法体被隐式加锁），把最外层 monitor 块去掉。
 *
 * Kotlin 转换说明：`getSubBlocks()` 原地修改通过向下转型为 `MutableList` 完成；
 * 对象引用比较用 `===`；静态方法 [removeSynchronized] 放入 companion + `@JvmStatic`。
 */
class SynchronizedRegionMaker(private val mth: MethodNode, private val regionMaker: RegionMaker) {

	fun process(curRegion: IRegion, block: BlockNode, insn: InsnNode, stack: RegionStack): BlockNode? {
		val synchRegion = SynchronizedRegion(curRegion, insn)
		@Suppress("UNCHECKED_CAST")
		(synchRegion.getSubBlocks() as MutableList<IContainer>).add(block)
		@Suppress("UNCHECKED_CAST")
		(curRegion.getSubBlocks() as MutableList<IContainer>).add(synchRegion)

		val exits: MutableSet<BlockNode> = LinkedHashSet()
		val cacheSet: MutableSet<BlockNode> = HashSet()
		traverseMonitorExits(synchRegion, insn.getArg(0), block, exits, cacheSet)

		for (exitInsn in synchRegion.getExitInsns()) {
			val insnBlock = BlockUtils.getBlockByInsn(mth, exitInsn)
			if (insnBlock != null) {
				insnBlock.add(AFlag.DONT_GENERATE)
			}
			// 移除 MONITOR_EXIT 的参数，以便内联进 MONITOR_ENTER
			exitInsn.removeArg(0)
			exitInsn.add(AFlag.DONT_GENERATE)
		}

		val body = BlockUtils.getNextBlock(block)
		if (body == null) {
			mth.addWarn("Unexpected end of synchronized block")
			return null
		}
		var exit: BlockNode? = null
		if (exits.size == 1) {
			exit = BlockUtils.getNextBlock(exits.iterator().next())
		} else if (exits.size > 1) {
			cacheSet.clear()
			exit = traverseMonitorExitsCross(body, exits, cacheSet)
		}

		stack.push(synchRegion)
		if (exit != null) {
			stack.addExit(exit)
		} else {
			for (exitBlock in exits) {
				// 不要把通向方法末尾（return/throw 等）的块加入退出边界
				val list = BlockUtils.buildSimplePath(exitBlock)
				if (list.isEmpty() || !BlockUtils.isExitBlock(mth, checkNotNull(Utils.last(list)))) {
					stack.addExit(exitBlock)
					// 仍然把它当作退出块，确保会被访问
					exit = exitBlock
				}
			}
		}
		@Suppress("UNCHECKED_CAST")
		(synchRegion.getSubBlocks() as MutableList<IContainer>).add(regionMaker.makeRegion(body))
		stack.pop()
		return exit
	}

	/** 从 monitor-enter 出发，沿后继收集包含 monitor-exit 的块 */
	private fun traverseMonitorExits(
		region: SynchronizedRegion,
		arg: InsnArg,
		block: BlockNode,
		exits: MutableSet<BlockNode>,
		visited: MutableSet<BlockNode>,
	) {
		visited.add(block)
		for (insn in block.getInstructions()) {
			if (insn.getType() == InsnType.MONITOR_EXIT &&
				insn.getArgsCount() > 0 &&
				insn.getArg(0) == arg
			) {
				exits.add(block)
				region.getExitInsns().add(insn)
				return
			}
		}
		for (node in block.getSuccessors()) {
			if (!visited.contains(node)) {
				traverseMonitorExits(region, arg, node, exits, visited)
			}
		}
	}

	/** 从 monitor-enter 出发，沿后继搜索与所有退出路径相交的块 */
	private fun traverseMonitorExitsCross(block: BlockNode, exits: Set<BlockNode>, visited: MutableSet<BlockNode>): BlockNode? {
		visited.add(block)
		for (node in checkNotNull(block.getCleanSuccessors())) {
			var cross = true
			for (exitBlock in exits) {
				val p = BlockUtils.isPathExists(exitBlock, node)
				if (!p) {
					cross = false
					break
				}
			}
			if (cross) {
				return node
			}
			if (!visited.contains(node)) {
				val res = traverseMonitorExitsCross(node, exits, visited)
				if (res != null) {
					return res
				}
			}
		}
		return null
	}

	companion object {
		@JvmStatic
		fun removeSynchronized(mth: MethodNode) {
			val startRegion = checkNotNull(mth.region)
			val subBlocks = startRegion.getSubBlocks()
			if (subBlocks.isNotEmpty() && subBlocks[0] is SynchronizedRegion) {
				val synchRegion = subBlocks[0] as SynchronizedRegion
				val syncInsn = synchRegion.getEnterInsn()
				if (canRemoveSyncBlock(mth, syncInsn)) {
					// 用内层区域替换 synchronized 块
					@Suppress("UNCHECKED_CAST")
					(startRegion.getSubBlocks() as MutableList<IContainer>).set(0, synchRegion.getRegion())
					// 移除 monitor-enter 指令
					InsnRemover.remove(mth, syncInsn)
					// 移除 monitor-exit 指令
					for (exit in synchRegion.getExitInsns()) {
						InsnRemover.remove(mth, exit)
					}
					// 再次清理区域
					CleanRegions.process(mth)
				}
			}
		}

		private fun canRemoveSyncBlock(mth: MethodNode, synchInsn: InsnNode): Boolean {
			val syncArg = synchInsn.getArg(0)
			if (mth.accessFlags.isStatic()) {
				if (syncArg.isInsnWrap && syncArg.isConst()) {
					val constInsn = syncArg.unwrap()
					if (constInsn != null && constInsn.getType() == InsnType.CONST_CLASS) {
						val clsType = (constInsn as ConstClassNode).clsType
						if (clsType == mth.parentClass.getType()) {
							return true
						}
					}
				}
				mth.addWarnComment("In static synchronized method top region not synchronized by class const: " + syncArg)
			} else {
				if (syncArg.isThis()) {
					return true
				}
				mth.addWarnComment("In synchronized method top region not synchronized by 'this': " + syncArg)
			}
			return false
		}
	}
}
