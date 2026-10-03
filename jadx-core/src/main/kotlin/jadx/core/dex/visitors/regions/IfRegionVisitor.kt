package jadx.core.dex.visitors.regions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.conditions.IfCondition
import jadx.core.dex.regions.conditions.IfRegion
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.InsnUtils
import jadx.core.utils.RegionUtils

/**
 * 优化 if/else 区域：调整分支顺序、标记 else-if 链、删除冗余 else。
 *
 * **算法意图**：编译器生成的跳转往往与人类写法相反（条件取反、then/else 对调）。
 * 本访问器按一套启发式规则决定是否把 if 取反，使输出更接近源码；并在
 * 确定 then 分支必然退出（return/throw）时，把 `else { ... }` 提升为顺序代码，
 * 去掉多余的 `else` 关键字。
 *
 * Kotlin 转换说明：
 * - 原 Java 的静态方法 `processIfRequested`/`process` 放入 companion + `@JvmStatic`；
 * - 原 Java 的可空容器参数在 Kotlin 中显式标注/判空，保持原有分支逻辑。
 */
class IfRegionVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		process(mth)
	}

	/** 处理 if 区域（含三元表达式转换与冗余 else 删除） */
	private class ProcessIfRegionVisitor : AbstractRegionVisitor() {
		override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
			if (region is IfRegion) {
				orderBranches(mth, region)
				markElseIfChains(mth, region)
			}
			return true
		}
	}

	/** 迭代删除冗余 else 的访问器 */
	private class RemoveRedundantElseVisitor : IRegionIterativeVisitor {
		override fun visitRegion(mth: MethodNode, region: IRegion): Boolean {
			if (region is IfRegion) {
				return removeRedundantElseBlock(mth, region)
			}
			return false
		}
	}

	companion object {
		private val PROCESS_IF_REGION_VISITOR: IRegionVisitor = ProcessIfRegionVisitor()
		private val REMOVE_REDUNDANT_ELSE_VISITOR: IRegionIterativeVisitor = RemoveRedundantElseVisitor()

		/** 若方法被标记为需要重新优化 if 区域，则执行一次并清除标记 */
		fun processIfRequested(mth: MethodNode) {
			if (mth.contains(AFlag.REQUEST_IF_REGION_OPTIMIZE)) {
				try {
					process(mth)
				} finally {
					mth.remove(AFlag.REQUEST_IF_REGION_OPTIMIZE)
				}
			}
		}

		private fun process(mth: MethodNode) {
			TernaryMod.process(mth)
			DepthRegionTraversal.traverse(mth, PROCESS_IF_REGION_VISITOR)
			DepthRegionTraversal.traverseIterative(mth, REMOVE_REDUNDANT_ELSE_VISITOR)
		}

		/**
		 * 决定是否交换 then/else 分支（即对条件取反）。
		 *
		 * 规则优先级：空分支 > 源码行号提示 > 条件化简 > 分支大小与出口块 > break 指令。
		 */
		private fun orderBranches(mth: MethodNode, ifRegion: IfRegion) {
			if (RegionUtils.isEmpty(ifRegion.elseRegion)) {
				return
			}
			if (RegionUtils.isEmpty(ifRegion.thenRegion)) {
				invertIfRegion(ifRegion)
				return
			}
			val thenRegion = checkNotNull(ifRegion.thenRegion)
			val elseRegion = checkNotNull(ifRegion.elseRegion)
			if (mth.contains(AFlag.USE_LINES_HINTS)) {
				val thenLine = RegionUtils.getFirstSourceLine(thenRegion)
				val elseLine = RegionUtils.getFirstSourceLine(elseRegion)
				if (thenLine != 0 && elseLine != 0) {
					if (thenLine > elseLine) {
						invertIfRegion(ifRegion)
					}
					return
				}
			}
			if (ifRegion.simplifyCondition()) {
				val condition = ifRegion.condition
				if (condition != null && condition.mode == IfCondition.Mode.NOT) {
					invertIfRegion(ifRegion)
				}
			}
			val thenSize = RegionUtils.insnsCount(thenRegion)
			val elseSize = RegionUtils.insnsCount(elseRegion)
			if (isSimpleExitBlock(mth, elseRegion)) {
				if (isSimpleExitBlock(mth, thenRegion)) {
					if (elseSize < thenSize) {
						invertIfRegion(ifRegion)
						return
					}
				}
				if (elseSize == 1) {
					val lastRegion = RegionUtils.hasExitEdge(ifRegion)
					if (lastRegion && mth.isVoidReturn()) {
						val lastElseInsn = RegionUtils.getLastInsn(elseRegion)
						if (InsnUtils.isInsnType(lastElseInsn, InsnType.THROW)) {
							// 把 throw 移到 then 分支
							invertIfRegion(ifRegion)
						} else {
							// 方法末尾的单个 return 稍后会被删除
						}
						return
					}
					if (thenSize > 2 && !(lastRegion && thenSize < 4)) {
						invertIfRegion(ifRegion)
						return
					}
				}
			}
			val thenExit = RegionUtils.hasExitBlock(thenRegion)
			val elseExit = RegionUtils.hasExitBlock(elseRegion)
			if (elseExit && (!thenExit || elseSize < thenSize)) {
				invertIfRegion(ifRegion)
				return
			}
			// 把 then 分支里的 if 提升出来，形成 else-if 链
			if (isIfRegion(ifRegion.thenRegion) &&
				!isIfRegion(ifRegion.elseRegion) &&
				!thenExit
			) {
				invertIfRegion(ifRegion)
				return
			}
			// 把 break 移进 then 分支
			if (RegionUtils.hasBreakInsn(elseRegion)) {
				invertIfRegion(ifRegion)
				return
			}
		}
		private fun isIfRegion(container: IContainer?): Boolean {
			if (container is IfRegion) {
				return true
			}
			if (container is IRegion) {
				val subBlocks = container.subBlocks
				return subBlocks.size == 1 && subBlocks[0] is IfRegion
			}
			return false
		}

		/** 标记 else-if 链：`else { if (...) ... }` 中的内层 if 打上标记 */
		private fun markElseIfChains(mth: MethodNode, ifRegion: IfRegion) {
			if (isSimpleExitBlock(mth, ifRegion.thenRegion)) {
				return
			}
			val elsRegion = ifRegion.elseRegion
			if (elsRegion is Region) {
				val subBlocks = elsRegion.subBlocks
				if (subBlocks.size == 1 && subBlocks[0] is IfRegion) {
					subBlocks[0].add(AFlag.ELSE_IF_CHAIN)
					elsRegion.add(AFlag.ELSE_IF_CHAIN)
				}
			}
		}

		/**
		 * 删除冗余 else：若 then 分支一定退出（return/throw），
		 * 则把 else 内容与 then 内容平铺到同一区域。
		 */
		private fun removeRedundantElseBlock(mth: MethodNode, ifRegion: IfRegion): Boolean {
			if (ifRegion.elseRegion == null) {
				return false
			}
			if (!RegionUtils.hasExitBlock(ifRegion.thenRegion)) {
				return false
			}
			val thenRegion = checkNotNull(ifRegion.thenRegion)
			val elseRegion = checkNotNull(ifRegion.elseRegion)
			val lastThanInsn = RegionUtils.getLastInsn(thenRegion)
			if (InsnUtils.isInsnType(lastThanInsn, InsnType.THROW)) {
				// throw 之后总是可以省略 else
			} else {
				// 代码风格检查：then/else 各只有一条指令时保留，避免风格突变
				if (mth.isVoidReturn()) {
					val thenSize = RegionUtils.insnsCount(thenRegion)
					if (thenSize < 5) {
						val elseSize = RegionUtils.insnsCount(elseRegion)
						val range = if (elseRegion.contains(AFlag.ELSE_IF_CHAIN)) 4 else 2
						if (thenSize == elseSize || (thenSize * range > elseSize && thenSize < elseSize * range)) {
							return false
						}
					}
				}
			}
			val parent = checkNotNull(ifRegion.parent)
			val newRegion = Region(parent)
			if (parent.replaceSubBlock(ifRegion, newRegion)) {
				newRegion.add(ifRegion)
				newRegion.add(elseRegion)
				ifRegion.setElseRegion(null)
				return true
			}
			return false
		}

		private fun invertIfRegion(ifRegion: IfRegion) {
			val elseRegion = ifRegion.elseRegion
			if (elseRegion != null) {
				ifRegion.invert()
			}
		}

		/** 判断容器是否“简单出口块”（直接 return 或指向方法出口） */
		private fun isSimpleExitBlock(mth: MethodNode, container: IContainer?): Boolean {
			if (container == null) {
				return false
			}
			if (container.contains(AFlag.RETURN) || RegionUtils.isExitBlock(mth, container)) {
				return true
			}
			if (container is IRegion) {
				val subBlocks = container.subBlocks
				return subBlocks.size == 1 && RegionUtils.isExitBlock(mth, subBlocks[0])
			}
			return false
		}
	}
}
