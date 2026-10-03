package jadx.gui.utils.pkgs

import jadx.api.JavaPackage
import jadx.core.dex.info.PackageInfo
import jadx.core.utils.ListUtils
import jadx.core.utils.Utils
import jadx.gui.JadxWrapper
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JPackage
import jadx.gui.utils.JNodeCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 包树构建助手。
 *
 * **做什么**：把 `jadx` 的 [JavaPackage] 列表转换为界面树节点 [JPackage]，
 * 支持“扁平包”与“层级包”两种模式，并合并仅含单个子目录的中间包。
 *
 * **为什么不是 `data class`**：持有缓存与可变状态，按身份使用。
 */
class PackageHelper(private val wrapper: JadxWrapper, private val nodeCache: JNodeCache) {

	private var excludedPackages: List<String> = emptyList()
	private val pkgInfoMap: MutableMap<PackageInfo, JPackage> = HashMap()

	/** 构建包树根节点。 */
	fun getRoots(flatPackages: Boolean): List<JPackage> {
		excludedPackages = wrapper.getExcludedPackages()
		pkgInfoMap.clear()
		if (flatPackages) {
			return prepareFlatPackages()
		}
		val start = System.currentTimeMillis()
		val roots = prepareHierarchyPackages()
		if (LOG.isDebugEnabled) {
			LOG.debug("Prepare hierarchy packages in {} ms", System.currentTimeMillis() - start)
		}
		return roots
	}

	/** 收集某个包及其父链上可重命名的包节点。 */
	fun getRenameNodes(pkg: JPackage): List<JRenamePackage> {
		val list = ArrayList<JRenamePackage>()
		var pkgInfo: PackageInfo? = checkNotNull(pkg.getPkg()).getPkgNode().getAliasPkgInfo()
		val added = HashSet<String>()
		while (pkgInfo != null) {
			val jPkg = pkgInfoMap[pkgInfo]
			if (jPkg != null && !jPkg.isSynthetic) {
				val javaPkg = jPkg.getPkg()
				if (javaPkg != null && !javaPkg.isDefault()) {
					val renamePkg = JRenamePackage(javaPkg, javaPkg.getRawFullName(), javaPkg.getFullName(), javaPkg.getName())
					if (added.add(javaPkg.getFullName())) {
						list.add(renamePkg)
					}
				}
			}
			pkgInfo = pkgInfo.parentPkg
		}
		return list
	}

	private fun prepareFlatPackages(): List<JPackage> {
		val list = ArrayList<JPackage>()
		for (javaPkg in wrapper.packages) {
			if (javaPkg.isLeaf() || javaPkg.getClasses().isNotEmpty()) {
				val pkg = buildJPackage(javaPkg, false)
				pkg.setName(javaPkg.getFullName())
				list.add(pkg)
				pkgInfoMap[javaPkg.getPkgNode().getAliasPkgInfo()] = pkg
			}
		}
		list.sortWith(PKG_COMPARATOR)
		return list
	}

	private fun prepareHierarchyPackages(): List<JPackage> {
		val root = JPackage.makeTmpRoot()
		val packages = wrapper.packages
		val jPackages = ArrayList<JPackage>(packages.size)
		// 为已存在的包创建节点
		for (javaPkg in packages) {
			val jPkg = buildJPackage(javaPkg, false)
			jPackages.add(jPkg)
			val aliasPkgInfo = javaPkg.getPkgNode().getAliasPkgInfo()
			jPkg.setName(aliasPkgInfo.name)
			pkgInfoMap[aliasPkgInfo] = jPkg
			if (aliasPkgInfo.isRoot()) {
				mutableSubPackages(root).add(jPkg)
			}
		}
		// 连接子包，并为重命名产生的包补建缺失的父包
		for (jPkg in jPackages) {
			if (checkNotNull(jPkg.getPkg()).isLeaf()) {
				buildLeafPath(jPkg, root, pkgInfoMap)
			}
		}
		val toMerge = ArrayList<JPackage>()
		traverseMiddlePackages(root, toMerge)
		Utils.treeDfsVisit(root, { pkg: JPackage -> pkg.getSubPackages() }) { v: JPackage ->
			mutableSubPackages(v).sortWith(PKG_COMPARATOR)
		}
		return root.getSubPackages()
	}

	private fun buildLeafPath(jPkg: JPackage, root: JPackage, pkgMap: MutableMap<PackageInfo, JPackage>) {
		var currentJPkg = jPkg
		var current: PackageInfo? = checkNotNull(jPkg.getPkg()).getPkgNode().getAliasPkgInfo().parentPkg
		while (current != null) {
			var parentJPkg = pkgMap[current]
			if (parentJPkg == null) {
				parentJPkg = buildJPackage(checkNotNull(currentJPkg.getPkg()), true)
				parentJPkg.setName(current.name)
				pkgMap[current] = parentJPkg
				if (current.isRoot()) {
					mutableSubPackages(root).add(parentJPkg)
				}
			}
			val subPackages = mutableSubPackages(parentJPkg)
			val pkgName = currentJPkg.getName()
			if (ListUtils.noneMatch(subPackages) { p -> p.getName() == pkgName }) {
				subPackages.add(currentJPkg)
			}
			currentJPkg = parentJPkg
			current = current.parentPkg
		}
	}

	private fun traverseMiddlePackages(pkg: JPackage, toMerge: MutableList<JPackage>) {
		val subPackages = mutableSubPackages(pkg)
		val count = subPackages.size
		for (i in 0 until count) {
			val subPackage = subPackages[i]
			val replacePkg = mergeMiddlePackages(subPackage, toMerge)
			if (replacePkg !== subPackage) {
				subPackages[i] = replacePkg
			}
			traverseMiddlePackages(replacePkg, toMerge)
		}
	}

	private fun mergeMiddlePackages(jPkg: JPackage, merged: MutableList<JPackage>): JPackage {
		val subPackages = mutableSubPackages(jPkg)
		if (subPackages.size == 1 && jPkg.getClasses().isEmpty()) {
			merged.add(jPkg)
			val endPkg = mergeMiddlePackages(subPackages[0], merged)
			merged.clear()
			return endPkg
		}
		if (merged.isNotEmpty()) {
			merged.add(jPkg)
			jPkg.setName(Utils.listToString(merged, ".", { pkg: JPackage -> pkg.getName() }))
		}
		return jPkg
	}

	private fun buildJPackage(javaPkg: JavaPackage, synthetic: Boolean): JPackage {
		val pkgEnabled = isPkgEnabled(javaPkg.getRawFullName(), excludedPackages)
		val classes: List<JClass>
		if (synthetic) {
			classes = emptyList()
		} else {
			val mapped = Utils.collectionMap(javaPkg.getClassesNoDup()) { cls -> checkNotNull(nodeCache.makeFrom(cls)) }
			val mutableClasses = mapped.toMutableList()
			mutableClasses.sortWith(CLASS_COMPARATOR)
			classes = mutableClasses
		}
		return nodeCache.newJPackage(javaPkg, synthetic, pkgEnabled, classes)
	}

	private fun isPkgEnabled(fullPkgName: String, excludedPackages: List<String>): Boolean = excludedPackages.isEmpty() || excludedPackages.none { p -> isPkgMatch(fullPkgName, p) }

	private fun isPkgMatch(fullPkgName: String, filterPkg: String): Boolean {
		if (fullPkgName == filterPkg) {
			return true
		}
		// 优化判断，等价于 `fullPkgName.startsWith(filterPkg + '.')`
		val filterPkgLen = filterPkg.length
		return fullPkgName.length > filterPkgLen &&
			fullPkgName[filterPkgLen] == '.' &&
			fullPkgName.startsWith(filterPkg)
	}

	@Suppress("UNCHECKED_CAST")
	private fun mutableSubPackages(pkg: JPackage): MutableList<JPackage> = pkg.getSubPackages() as MutableList<JPackage>

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(PackageHelper::class.java)

		private val CLASS_COMPARATOR: Comparator<JClass> =
			Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()) }

		private val PKG_COMPARATOR: Comparator<JPackage> =
			Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()) }
	}
}
