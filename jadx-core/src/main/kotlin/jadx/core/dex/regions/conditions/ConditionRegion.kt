package jadx.core.dex.regions.conditions

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IConditionRegion
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.regions.AbstractRegion
import jadx.core.utils.BlockUtils

/**
 * 带条件的区域的公共基类（[IfRegion]、[jadx.core.dex.regions.loops.LoopRegion] 等）。
 *
 * **职责**：保存该区域的条件表达式 [condition] 以及参与条件计算的块列表 [conditionBlocks]。
 * [conditionBlocks] 用于“回溯”：把条件相关的多个块合并展示，并定位条件所在的源码行。
 *
 * Kotlin 转换说明：
 * - 字段声明为私有属性（[condition]/[conditionBlocks]），对外仍用显式
 *   `getCondition()/getConditionBlocks()` 方法名，避免与 [IConditionRegion] 的抽象方法冲突；
 * - 原 Java 的 `updated != condition` 是引用比较，这里必须写成 `!==`。
 */
abstract class ConditionRegion(parent: IRegion?) :
	AbstractRegion(parent),
	IConditionRegion {

	/** 条件表达式；可能为 null（例如循环头还没解析出条件） */
	private var condition: IfCondition? = null

	/** 参与条件计算的块列表，默认空列表 */
	private var conditionBlocks: List<BlockNode> = emptyList()

	override fun getCondition(): IfCondition? = condition

	override fun getConditionBlocks(): List<BlockNode> = conditionBlocks

	override fun invertCondition() {
		val cond = condition
		if (cond != null) {
			condition = IfCondition.invert(cond)
		}
	}

	override fun simplifyCondition(): Boolean {
		val cond = condition ?: return false
		val updated = IfCondition.simplify(cond)
		if (updated !== cond) {
			condition = updated
			return true
		}
		return false
	}

	override fun getConditionSourceLine(): Int {
		for (block in conditionBlocks) {
			val lastInsn = BlockUtils.getLastInsn(block)
			if (lastInsn != null) {
				val sourceLine = lastInsn.getSourceLine()
				if (sourceLine != 0) {
					return sourceLine
				}
			}
		}
		return 0
	}

	/**
	 * 用 [IfInfo] 更新条件信息（推荐方式）。
	 *
	 * [IfInfo.getMergedBlocks] 是位图集合，这里转成普通列表保存，便于后续遍历。
	 */
	fun updateCondition(info: IfInfo) {
		this.condition = info.getCondition()
		this.conditionBlocks = info.getMergedBlocks().toList()
	}

	fun updateCondition(condition: IfCondition, conditionBlocks: List<BlockNode>) {
		this.condition = condition
		this.conditionBlocks = conditionBlocks
	}

	fun updateCondition(block: BlockNode) {
		this.condition = IfCondition.fromIfBlock(block)
		this.conditionBlocks = listOf(block)
	}
}
