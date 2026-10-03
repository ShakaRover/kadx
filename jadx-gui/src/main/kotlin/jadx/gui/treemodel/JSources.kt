package jadx.gui.treemodel

import jadx.gui.JadxWrapper
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.pkgs.PackageHelper
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * 源码根节点（“源码”子树）。
 *
 * **做什么**：在树中展示所有反编译出的包与类，支持“扁平包”与“层级包”两种模式。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class JSources(jRoot: JRoot, private val wrapper: JadxWrapper) : JNode() {

	private val flatPackages: Boolean = jRoot.isFlatPackages

	init {
		update()
	}

	fun update() {
		removeAllChildren()
		val packageHelper: PackageHelper = wrapper.cache.getPackageHelper()
		val roots = packageHelper.getRoots(flatPackages)
		for (rootPkg in roots) {
			rootPkg.update()
			add(rootPkg)
		}
	}

	override fun getIcon(): Icon = ROOT_ICON

	override fun getJParent(): JClass? = null

	override fun getID(): String = "JSources"

	override fun makeString(): String = NLS.str("tree.sources_title")

	companion object {
		private const val serialVersionUID = 8962924556824862801L

		private val ROOT_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/packageClasses")
	}
}
