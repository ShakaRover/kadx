package jadx.core.dex.nodes

import jadx.core.dex.regions.conditions.IfCondition

interface IConditionRegion : IRegion {
	fun getCondition(): IfCondition?

	/**
	 * Blocks merged into condition
	 * Needed for backtracking
	 */
	fun getConditionBlocks(): List<BlockNode>

	fun invertCondition()

	fun simplifyCondition(): Boolean

	fun getConditionSourceLine(): Int
}
