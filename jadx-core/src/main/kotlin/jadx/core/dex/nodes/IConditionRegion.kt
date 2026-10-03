package jadx.core.dex.nodes

import jadx.core.dex.regions.conditions.IfCondition

interface IConditionRegion : IRegion {
	val condition: IfCondition?

	/**
	 * Blocks merged into condition
	 * Needed for backtracking
	 */
	val conditionBlocks: List<BlockNode>

	fun invertCondition()

	fun simplifyCondition(): Boolean

	val conditionSourceLine: Int
}
