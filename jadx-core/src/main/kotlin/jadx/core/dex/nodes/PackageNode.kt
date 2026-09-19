package jadx.core.dex.nodes

import jadx.api.JavaPackage
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.attributes.nodes.LineAttrNode
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.PackageInfo
import jadx.core.utils.StringUtils.containsChar

class PackageNode(
	val root: RootNode,
	val parentPkg: PackageNode?,
	val pkgInfo: PackageInfo,
) : LineAttrNode(),
	IPackageUpdate,
	IDexNode,
	ICodeNodeRef,
	Comparable<PackageNode> {
	companion object {
		fun getForClass(root: RootNode, fullPkg: String, cls: ClassNode): PackageNode {
			val pkg = getOrBuild(root, fullPkg)
			pkg.classes.add(cls)
			return pkg
		}

		fun getOrBuild(root: RootNode, fullPkg: String): PackageNode {
			val existPkg = root.resolvePackage(fullPkg)
			if (existPkg != null) return existPkg
			val pgkInfo = PackageInfo.fromFullPkg(root, fullPkg)
			val parentPkg = getParentPkg(root, pgkInfo)
			val pkgNode = PackageNode(root, parentPkg, pgkInfo)
			if (parentPkg != null) {
				parentPkg.subPackages.add(pkgNode)
			}
			root.addPackage(pkgNode)
			return pkgNode
		}

		private fun getParentPkg(root: RootNode, pgkInfo: PackageInfo): PackageNode? {
			val parentPkg = pgkInfo.parentPkg ?: return null
			return getOrBuild(root, parentPkg.fullName)
		}
	}

	var aliasPkgInfo: PackageInfo = pkgInfo
	val subPackages = ArrayList<PackageNode>()
	val classes = ArrayList<ClassNode>()
	var javaNode: JavaPackage? = null

	override fun rename(newName: String) {
		rename(newName, true)
	}

	fun rename(newName: String, runUpdates: Boolean) {
		val alias: String
		val isFullAlias: Boolean
		if (containsChar(newName, '/')) {
			alias = newName.replace('/', '.')
			isFullAlias = true
		} else if (newName.startsWith(".")) {
			alias = newName.substring(1)
			isFullAlias = true
		} else {
			alias = newName
			isFullAlias = containsChar(newName, '.')
		}
		if (isFullAlias) {
			setFullAlias(alias, runUpdates)
		} else {
			setLeafAlias(alias, runUpdates)
		}
	}

	fun setLeafAlias(alias: String, runUpdates: Boolean) {
		if (pkgInfo.name == alias) {
			this.aliasPkgInfo = pkgInfo
		} else {
			this.aliasPkgInfo = PackageInfo.fromShortName(root, getParentAliasPkgInfo(), alias)
		}
		if (runUpdates) {
			updatePackages(this)
		}
	}

	fun setFullAlias(fullAlias: String, runUpdates: Boolean) {
		if (pkgInfo.fullName == fullAlias) {
			this.aliasPkgInfo = pkgInfo
		} else {
			this.aliasPkgInfo = PackageInfo.fromFullPkg(root, fullAlias)
		}
		if (runUpdates) {
			updatePackages(this)
		}
	}

	override fun onParentPackageUpdate(updatedPkg: PackageNode) {
		aliasPkgInfo = PackageInfo.fromShortName(root, getParentAliasPkgInfo(), aliasPkgInfo.name)
		updatePackages(updatedPkg)
	}

	fun updatePackages() {
		updatePackages(this)
	}

	private fun updatePackages(updatedPkg: PackageNode) {
		for (subPackage in subPackages) {
			subPackage.onParentPackageUpdate(updatedPkg)
		}
		for (cls in classes) {
			cls.onParentPackageUpdate(updatedPkg)
		}
	}

	fun getName(): String = pkgInfo.name

	fun getFullName(): String = pkgInfo.fullName

	fun getPkgInfo(): PackageInfo = pkgInfo

	fun getAliasPkgInfo(): PackageInfo = aliasPkgInfo

	fun hasAlias(): Boolean {
		if (pkgInfo === aliasPkgInfo) return false
		return pkgInfo.name != aliasPkgInfo.name
	}

	fun hasParentAlias(): Boolean {
		if (pkgInfo === aliasPkgInfo) return false
		return pkgInfo.parentPkg != aliasPkgInfo.parentPkg
	}

	fun removeAlias() {
		aliasPkgInfo = pkgInfo
	}

	fun getParentPkg(): PackageNode? = parentPkg

	fun getParentAliasPkgInfo(): PackageInfo? = parentPkg?.aliasPkgInfo

	fun isRoot(): Boolean = parentPkg == null

	fun isLeaf(): Boolean = subPackages.isEmpty()

	fun getSubPackages(): List<PackageNode> = subPackages

	fun isEmpty(): Boolean = classes.isEmpty() && subPackages.isEmpty()

	fun getClasses(): List<ClassNode> = classes

	fun getClassesNoDup(): List<ClassNode> {
		val set = HashSet<ClassInfo>()
		for (cls in classes) {
			set.add(cls.classInfo)
		}
		return set.mapNotNull { ci -> root.resolveClass(ci as ClassInfo) }
	}

	override fun typeName(): String = "package"

	override fun getAnnType() = ICodeAnnotation.AnnType.PKG

	override fun root(): RootNode = root

	override fun getInputFileName(): String? = ""

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is PackageNode) return false
		return pkgInfo == other.pkgInfo
	}

	override fun hashCode(): Int = pkgInfo.hashCode()

	override fun compareTo(other: PackageNode): Int = pkgInfo.fullName.compareTo(other.pkgInfo.fullName)

	override fun toString(): String = pkgInfo.fullName
}
