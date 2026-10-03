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
 * - 字段声明为私有属性（[conditionValue]/[conditionBlocksValue]），对外以 [condition]/[conditionBlocks]
 *   属性暴露，JVM 上仍生成 `getCondition()/getConditionBlocks()`；
 * - 原 Java 的 `updated != condition` 是引用比较，这里必须写成 `!==`。
 */
abstract class ConditionRegion(parent: IRegion?) :
	AbstractRegion(parent),
	IConditionRegion {

	/** 条件表达式；可能为 null（例如循环头还没解析出条件） */
	private var conditionValue: IfCondition? = null

	/** 参与条件计算的块列表，默认空列表 */
	private var conditionBlocksValue: List<BlockNode> = emptyList()

	override val condition: IfCondition? get() = conditionValue

	override val conditionBlocks: List<BlockNode> get() = conditionBlocksValue

	override fun invertCondition() {
		val cond = conditionValue
		if (cond != null) {
			conditionValue = IfCondition.invert(cond)
		}
	}

	override fun simplifyCondition(): Boolean {
		val cond = conditionValue ?: return false
		val updated = IfCondition.simplify(cond)
		if (updated !== cond) {
			conditionValue = updated
			return true
		}
		return false
	}

	override val conditionSourceLine: Int get() {
		for (block in conditionBlocksValue) {
			val lastInsn = BlockUtils.getLastInsn(block)
			if (lastInsn != null) {
				val sourceLine = lastInsn.sourceLine
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
		this.conditionValue = info.condition
		this.conditionBlocksValue = info.mergedBlocks.toList()
	}

	fun updateCondition(condition: IfCondition, conditionBlocks: List<BlockNode>) {
		this.conditionValue = condition
		this.conditionBlocksValue = conditionBlocks
	}

	fun updateCondition(block: BlockNode) {
		this.conditionValue = IfCondition.fromIfBlock(block)
		this.conditionBlocksValue = listOf(block)
	}
}
