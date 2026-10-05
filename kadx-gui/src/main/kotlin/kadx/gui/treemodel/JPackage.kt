package kadx.gui.treemodel

import kadx.api.JavaNode
import kadx.api.JavaPackage
import kadx.gui.ui.MainWindow
import kadx.gui.ui.popupmenu.JPackagePopupMenu
import kadx.gui.utils.Icons
import kadx.gui.utils.UiUtils
import javax.swing.Icon
import javax.swing.JPopupMenu

/**
 * 包节点。
 *
 * **做什么**：类树的包层级节点。支持“扁平包”与“层级包”两种展示方式，
 * 并可通过 [synthetic] 标记由包别名/重命名产生的合成包。
 *
 * **为什么不是 `data class`**：节点相等性委托给 [pkg]，且树中存在父子互相引用。
 */
class JPackage(
	/** 包对应的 Java 视图；合成根包（[makeTmpRoot]）为 `null`。 */
	private val pkg: JavaPackage?,
	private val enabled: Boolean,
	private val classes: List<JClass>,
	private val subPackages: MutableList<JPackage>,
	/**
	 * 由完整包别名创建、没有对应原始包的“合成”包。
	 * 此时 [pkg] 指向最接近的原始包叶子。
	 */
	private val synthetic: Boolean,
) : JNode() {

	private var name: String = ""

	fun update() {
		removeAllChildren()
		if (isEnabled) {
			for (subPkg in subPackages) {
				subPkg.update()
				add(subPkg)
			}
			for (cls in classes) {
				cls.update()
				add(cls)
			}
		}
	}

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu = JPackagePopupMenu(mainWindow, this)

	fun getPkg(): JavaPackage? = pkg

	override fun getJavaNode(): JavaNode? = pkg

	override fun getName(): String = name

	fun setName(name: String) {
		this.name = name
	}

	fun getSubPackages(): List<JPackage> = subPackages

	fun getClasses(): List<JClass> = classes

	val isEnabled: Boolean get() = enabled

	val isSynthetic: Boolean get() = synthetic

	override fun getIcon(): Icon = Icons.PACKAGE

	override fun getJParent(): JClass? = null

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		// 原 Java 为 `pkg.equals(...)`（pkg 为 null 时会 NPE）；此处用可空相等避免崩溃
		return pkg == (other as JPackage).pkg
	}

	override fun hashCode(): Int = pkg?.hashCode() ?: 0

	override fun makeString(): String = name

	override fun makeStringHtml(): String {
		if (name.isEmpty()) {
			return PACKAGE_DEFAULT_HTML_STR
		}
		return name
	}

	override fun disableHtml(): Boolean {
		if (name.isEmpty()) {
			// 空包名需要展示 PACKAGE_DEFAULT_HTML_STR，因此不能禁用 HTML
			return false
		}
		return true
	}

	override fun makeLongString(): String = pkg?.getFullName() ?: ""

	override fun toString(): String = name

	companion object {
		private const val serialVersionUID = -4120718634156839804L

		/** 默认（空）包的 HTML 占位字符串。 */
		val PACKAGE_DEFAULT_HTML_STR: String =
			UiUtils.wrapHtml(UiUtils.fadeHtml(UiUtils.escapeHtml("<empty>")))

		/** 创建一个临时的合成根包，用于构建包层级。 */
		fun makeTmpRoot(): JPackage = JPackage(null, true, emptyList(), ArrayList(), true)
	}
}
