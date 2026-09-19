package jadx.core.dex.nodes

interface IBranchRegion : IRegion {
	/**
	 * Return list of branches in this region.
	 * NOTE: Contains 'null' elements for indicate empty branches.
	 */
	fun getBranches(): List<IContainer?>
}
