package jadx.core.dex.visitors.regions.variables

import jadx.core.dex.instructions.args.SSAVar

/**
 * 单个 SSA 变量的使用情况汇总。
 *
 * **用途**：[CollectUsageRegionVisitor] 在遍历区域树时，把每个 SSA 变量的所有
 * “赋值位置”和“使用位置”（均以 [UsePlace] 表示）收集到这里，
 * 供 [ProcessVariables] 决定变量应在哪个区域声明。
 *
 * Kotlin 转换说明：
 * - [assigns]/[uses] 需要原地追加元素，故用 `MutableList`；
 * - [var] 是 Kotlin 关键字，属性名改为 `varUsage`（私有实现细节，对外仍通过 [getVar] 暴露）；
 * - 保持普通 class（不是值对象），不生成 `equals/hashCode`。
 */
internal class VarUsage(private val varUsage: SSAVar?) {

	/** 赋值位置列表，初始容量 3 覆盖常见情况 */
	val assigns: MutableList<UsePlace> = ArrayList(3)

	/** 使用位置列表 */
	val uses: MutableList<UsePlace> = ArrayList(3)

	fun getVar(): SSAVar? = varUsage

	override fun toString(): String = '{' + (varUsage?.toShortString() ?: "-") + ", a:" + assigns + ", u:" + uses + '}'
}
