package jadx.core.dex.nodes

interface IPackageUpdate {
	fun onParentPackageUpdate(updatedPkg: PackageNode)
}
