package kadx.core.dex.visitors.regions

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.CodeFeaturesAttr
import kadx.core.dex.attributes.nodes.CodeFeaturesAttr.CodeFeature
import kadx.core.dex.attributes.nodes.RegionRefAttr
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.IBranchRegion
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.Region
import kadx.core.dex.regions.SynchronizedRegion
import kadx.core.dex.regions.SwitchRegion
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.KadxVisitor
import kadx.core.dex.visitors.regions.maker.SwitchRegionMaker
import kadx.core.utils.BlockParentContainer
import kadx.core.utils.BlockUtils
import kadx.core.utils.ListUtils
import kadx.core.utils.RegionUtils

/**
 * 优化 switch 中的 `break`：提取公共 break、删除不可达 break。
 *
 * **算法意图**：编译器生成的 switch 常把 `break` 重复写在每个分支末尾，
 * 或某些分支因为前一条指令已经 return/throw 而永远到不了 break。
 * 本访问器：
 * - [ExtractCommonBreak]：若所有分支末尾都是 break，把它提到父区域末尾，只写一次；
 * - [RemoveUnreachableBreak]：删除位于 exit 指令之后的 break。
 *
 * Kotlin 转换说明：
 * - 原 Java 用 `Supplier` 延迟创建访问器，这里改用 Kotlin 的 `() -> T` 函数类型；
 * - `getSubBlocks()` 的原地修改通过向下转型为 `MutableList` 完成（实际都是可变列表）。
 */
@KadxVisitor(
	name = "SwitchBreakVisitor",
	desc = "Optimize 'break' instruction: common code extract, remove unreachable",
	runAfter = [LoopRegionVisitor::class],
)
class SwitchBreakVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (CodeFeaturesAttr.contains(mth, CodeFeature.SWITCH)) {
			runSwitchTraverse(mth) { ExtractCommonBreak() }
			runSwitchTraverse(mth) { RemoveUnreachableBreak() }
			IfRegionVisitor.processIfRequested(mth)
		}
	}

	/**
	 * 若 break 出现在所有分支中，则把它提取到父区域末尾；
	 * 若所有分支都以 exit 指令结束，则删除父区域里已存在的公共 break。
	 */
	private class ExtractCommonBreak : BaseSwitchRegionVisitor() {
		override fun processRegion(mth: MethodNode, region: IRegion) {
			if (region is IBranchRegion && region !is SwitchRegion) {
				// 所有分支都有 break 时，提取到父区域
				processBranchRegion(mth, region)
			}
		}

		private fun processBranchRegion(mth: MethodNode, region: IRegion) {
			val parentRegion = checkNotNull(region.parent)
			if (parentRegion.contains(AFlag.FALL_THROUGH)) {
				// fallthrough 分支不能提取 break
				return
			}
			var dontAddCommonBreak = false
			val lastParentBlock = RegionUtils.getLastBlock(parentRegion)
			if (BlockUtils.containsExitInsn(lastParentBlock)) {
				if (isBreakBlock(lastParentBlock)) {
					// 父块已经包含 break
					dontAddCommonBreak = true
				} else {
					// return/throw/continue 之后不能再加 break
					return
				}
			}
			val branches = (region as IBranchRegion).branches
			var removeCommonBreak = true // 所有分支都有 exit 指令时，公共 break 不可达
			val forBreakRemove = ArrayList<BlockParentContainer>()
			for (branch in branches) {
				if (branch == null) {
					removeCommonBreak = false
					continue
				}
				val last = RegionUtils.getLastInsnWithBlock(branch) ?: return
				val lastInsn = last.insn
				if (lastInsn.type == InsnType.BREAK) {
					val block = last.block
					val parent = checkNotNull(RegionUtils.getBlockContainer(branch, block))
					forBreakRemove.add(BlockParentContainer(parent, block))
					removeCommonBreak = false
				} else if (!lastInsn.isExitEdgeInsn) {
					removeCommonBreak = false
				}
			}
			if (!forBreakRemove.isEmpty()) {
				// 确认存在公共 break
				for (breakData in forBreakRemove) {
					removeBreak(breakData.block, breakData.parent)
				}
				if (!dontAddCommonBreak) {
					addBreakRegion.add(parentRegion)
					// 新的 break 可能成为上层分支区域的公共 break，请求重跑检查
					requestReRun()
				}
				// 删除 break 后可能可以使用 else-if 链
				mth.add(AFlag.REQUEST_IF_REGION_OPTIMIZE)
			}
			if (removeCommonBreak && lastParentBlock != null) {
				removeBreak(lastParentBlock, parentRegion)
			}
		}
	}

	/** 删除不可达的 break：若最后一个块是 break，且其前一条指令已是 exit 指令 */
	private class RemoveUnreachableBreak : BaseSwitchRegionVisitor() {
		override fun processRegion(mth: MethodNode, region: IRegion) {
			val subBlocks = region.subBlocks
			val lastContainer = ListUtils.last(subBlocks)
			if (lastContainer is IBlock) {
				if (isBreakBlock(lastContainer) && isPrevInsnIsExit(lastContainer, subBlocks)) {
					removeBreak(lastContainer, region)
				}
			}
		}

		private fun isPrevInsnIsExit(breakBlock: IBlock, subBlocks: List<IContainer>): Boolean {
			var prevInsn: InsnNode? = null
			if (breakBlock.instructions.size > 1) {
				// 同一块内的前一条指令
				val insns = breakBlock.instructions
				prevInsn = insns[insns.size - 2]
			} else if (subBlocks.size > 1) {
				val prev = subBlocks[subBlocks.size - 2]
				if (prev is IBlock) {
					val insns = prev.instructions
					prevInsn = ListUtils.last(insns)
				}
			}
			return prevInsn != null && prevInsn.isExitEdgeInsn
		}
	}

	/**
	 * 对每个 switch 区域，运行一个新创建的 switch 访问器；
	 * 若访问器请求重跑，则再次遍历该 switch。
	 */
	private class IterativeSwitchRegionVisitor(
		private val builder: () -> BaseSwitchRegionVisitor,
	) : AbstractRegionVisitor() {
		/**
		 * 只有底层列表稳定可变的区域（[Region] 及委托其列表的 [SynchronizedRegion]）才能真正追加；
		 * 其余区域（IfRegion/LoopRegion/SwitchRegion/TryCatchRegion）的 subBlocks 是重建的
		 * 临时列表或不可变视图——上游在此强转会抛 UnsupportedOperationException 或静默丢失。
		 */
		private fun appendBreakContainer(region: IRegion, container: IContainer) {
			when (region) {
				is Region -> region.add(container)
				is SynchronizedRegion -> region.region.add(container)
				else -> {}
			}
		}

		override fun leaveRegion(mth: MethodNode, region: IRegion) {
			if (region is SwitchRegion) {
				val switchVisitor = builder()
				switchVisitor.setCurrentSwitch(region)
				var runAgain: Boolean
				var k = 0
				do {
					runAgain = false
					DepthRegionTraversal.traverse(mth, region, switchVisitor)
					if (switchVisitor.isReRunRequested()) {
						switchVisitor.reset()
						runAgain = true
					}
					if (k++ > 20) {
						// 不应出现 20 层嵌套 if
						mth.addWarnComment("Unexpected iteration count in SwitchBreakVisitor. Please report as an issue")
						break
					}
				} while (runAgain)
			}
		}
	}

	/** switch break 访问器基类：维护待处理集合与当前 switch */
	private abstract class BaseSwitchRegionVisitor : AbstractRegionVisitor() {
		protected val addBreakRegion: MutableSet<IRegion> = HashSet()
		protected val cleanupSet: MutableSet<IContainer> = HashSet()

		// 显式 setCurrentSwitch 与属性生成的 setter 同名，故底层字段改名避免 JVM 签名冲突
		private lateinit var currentSwitchRef: SwitchRegion
		private var reRunRequested = false

		abstract fun processRegion(mth: MethodNode, region: IRegion)

		override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
			processRegion(mth, region)
			return true
		}

		override fun leaveRegion(mth: MethodNode, region: IRegion) {
			if (addBreakRegion.contains(region)) {
				addBreakRegion.remove(region)
				SwitchRegionMaker.appendBreakContainer(region, SwitchRegionMaker.buildBreakContainer(currentSwitchRef))
			}
			if (cleanupSet.contains(region)) {
				cleanupSet.remove(region)
				@Suppress("UNCHECKED_CAST")
				(region.subBlocks as MutableList<IContainer>).removeAll { it.contains(AFlag.REMOVE) }
			}
		}

		/** 访问器重跑前调用，重置状态 */
		fun reset() {
			reRunRequested = false
			addBreakRegion.clear()
			cleanupSet.clear()
		}

		fun requestReRun() {
			reRunRequested = true
		}

		fun isReRunRequested(): Boolean = reRunRequested

		fun setCurrentSwitch(currentSwitch: SwitchRegion) {
			this.currentSwitchRef = currentSwitch
		}

		protected fun isBreakBlock(block: IBlock?): Boolean {
			if (block != null) {
				val lastInsn = ListUtils.last(block.instructions)
				if (lastInsn != null && lastInsn.type == InsnType.BREAK) {
					val regionRefAttr: RegionRefAttr? = lastInsn.get(AType.REGION_REF)
					return regionRefAttr != null && regionRefAttr.region === currentSwitchRef
				}
			}
			return false
		}

		protected fun removeBreak(breakBlock: IBlock, parentContainer: IContainer) {
			val instructions = breakBlock.instructions
			val last = ListUtils.last(instructions)
			if (last != null && last.type == InsnType.BREAK) {
				ListUtils.removeLast(instructions)
				if (instructions.isEmpty()) {
					breakBlock.add(AFlag.REMOVE)
					cleanupSet.add(parentContainer)
				}
			}
		}
	}

	companion object {
		private fun runSwitchTraverse(mth: MethodNode, builder: () -> BaseSwitchRegionVisitor) {
			DepthRegionTraversal.traverse(mth, IterativeSwitchRegionVisitor(builder))
		}
	}

	override fun getName(): String = "SwitchBreakVisitor"
}
