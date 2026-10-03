package jadx.gui.treemodel

import jadx.api.JavaNode
import jadx.api.JavaVariable
import jadx.api.data.ICodeRename
import jadx.api.data.impl.JadxCodeRef
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.api.metadata.ICodeNodeRef
import jadx.core.deobf.NameMapper
import jadx.gui.ui.MainWindow
import jadx.gui.utils.UiUtils
import javax.swing.Icon

/**
 * 变量节点（方法内部的局部变量 / 参数）。
 *
 * **做什么**：在方法代码页里展示某个 [JavaVariable] 的定义位置与名称，并支持重命名。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class JVariable(
	private val jMth: JMethod,
	private val `var`: JavaVariable,
) : JNode(),
	JRenameNode {

	val javaVarNode: JavaVariable get() = `var`

	override fun getJavaNode(): JavaNode = `var`

	override fun getRootClass(): JClass = jMth.getRootClass()

	override fun getCodeNodeRef(): ICodeNodeRef = `var`.getVarNode()

	override fun getJParent(): JClass = jMth.getJParent()

	override fun getPos(): Int = `var`.getDefPos()

	override fun getIcon(): Icon? = null

	/**
	 * 短名称。
	 *
	 * 原 Java 直接返回 `var.getName()`（可能为 `null`）；Kotlin 侧为保持
	 * `makeString()` 非空契约，这里对未命名变量回退为空串（仅极端情况下出现）。
	 */
	override fun makeString(): String = `var`.getName() ?: ""

	override fun makeLongString(): String = `var`.getFullName()

	override fun makeLongStringHtml(): String = UiUtils.typeFormatHtml(`var`.getName() ?: "", `var`.getType())

	override fun disableHtml(): Boolean = false

	override fun getTooltip(): String {
		val name = `var`.getName() + " (r" + `var`.getReg() + "v" + `var`.getSsa() + ")"
		val fullType = UiUtils.escapeHtml(`var`.getType().toString())
		return UiUtils.wrapHtml(fullType + ' ' + UiUtils.escapeHtml(name))
	}

	override fun canRename(): Boolean = `var`.getName() != null

	override fun getTitle(): String = makeLongStringHtml()

	override fun isValidName(newName: String): Boolean = NameMapper.isValidIdentifier(newName)

	override fun buildCodeRename(newName: String, renames: MutableSet<ICodeRename>): ICodeRename = JadxCodeRename(JadxNodeRef.forMth(`var`.getMth()), JadxCodeRef.forVar(`var`), newName)

	override fun removeAlias() {
		`var`.removeAlias()
	}

	override fun addUpdateNodes(toUpdate: MutableList<JavaNode>) {
		toUpdate.add(`var`.getMth())
	}

	override fun reload(mainWindow: MainWindow) {
	}

	companion object {
		private const val serialVersionUID = -3002100457834453783L
	}
}
