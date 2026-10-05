package kadx.core.dex.nodes

interface IPackageUpdate {
	fun onParentPackageUpdate(updatedPkg: PackageNode)
}
