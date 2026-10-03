package jadx.gui.treemodel

import jadx.api.JavaField
import jadx.api.JavaNode
import jadx.api.data.ICodeRename
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.api.metadata.ICodeNodeRef
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.AccessInfo
import jadx.gui.ui.MainWindow
import jadx.gui.ui.dialog.RenameDialog
import jadx.gui.utils.Icons
import jadx.gui.utils.UiUtils
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import java.util.Comparator
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

/**
 * 字段节点。
 *
 * **做什么**：在类树下展示一个 [JavaField]，并支持重命名、右键菜单与 tooltip。
 *
 * **为什么不是 `data class`**：节点相等性委托给 [field]，且需要按身份参与树比较。
 */
class JField(
	private val field: JavaField,
	private val jParent: JClass,
) : JNode(),
	JRenameNode {

	val javaField: JavaField get() = this.field

	override fun getJavaNode(): JavaNode = field

	override fun getCodeNodeRef(): ICodeNodeRef = field.getFieldNode()

	override fun getJParent(): JClass = jParent

	override fun getRootClass(): JClass = jParent.getRootClass()

	override fun canRename(): Boolean = !field.getFieldNode().contains(AFlag.DONT_RENAME)

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu = RenameDialog.buildRenamePopup(mainWindow, this)

	override fun getTitle(): String = makeLongStringHtml()

	override fun buildCodeRename(newName: String, renames: MutableSet<ICodeRename>): ICodeRename = JadxCodeRename(JadxNodeRef.forFld(field), newName)

	override fun isValidName(newName: String): Boolean = NameMapper.isValidIdentifier(newName)

	override fun removeAlias() {
		field.removeAlias()
	}

	override fun addUpdateNodes(toUpdate: MutableList<JavaNode>) {
		toUpdate.add(field)
		toUpdate.addAll(field.getUseIn())
	}

	override fun reload(mainWindow: MainWindow) {
		mainWindow.reloadTreePreservingState()
	}

	override fun getIcon(): Icon {
		val af: AccessInfo = field.getAccessFlags()
		return UiUtils.makeIcon(af, ICON_FLD_PUB, ICON_FLD_PRI, ICON_FLD_PRO, Icons.FIELD)
	}

	override fun getSyntaxName(): String = SyntaxConstants.SYNTAX_STYLE_JAVA

	override fun makeString(): String = UiUtils.typeFormat(field.getName(), field.getType())

	override fun makeStringHtml(): String = UiUtils.typeFormatHtml(field.getName(), field.getType())

	override fun makeLongString(): String = UiUtils.typeFormat(field.getFullName(), field.getType())

	override fun makeLongStringHtml(): String = UiUtils.typeFormatHtml(field.getFullName(), field.getType())

	override fun getTooltip(): String {
		val fullType = UiUtils.escapeHtml(field.getType().toString())
		return UiUtils.wrapHtml(fullType + ' ' + UiUtils.escapeHtml(field.getName()))
	}

	override fun makeDescString(): String = UiUtils.typeStr(field.getType()) + " " + field.getName()

	override fun disableHtml(): Boolean = false

	override fun hasDescString(): Boolean = false

	override fun hashCode(): Int = field.hashCode()

	override fun equals(other: Any?): Boolean = this === other || (other is JField && field == other.field)

	fun compareToFld(other: JField): Int = COMPARATOR.compare(this, other)

	override fun compareTo(other: JNode): Int {
		if (other is JField) {
			return compareToFld(other)
		}
		return super.compareTo(other)
	}

	companion object {
		private const val serialVersionUID = 1712572192106793359L

		private val ICON_FLD_PRI: ImageIcon = UiUtils.openSvgIcon("nodes/privateField")
		private val ICON_FLD_PRO: ImageIcon = UiUtils.openSvgIcon("nodes/protectedField")
		private val ICON_FLD_PUB: ImageIcon = UiUtils.openSvgIcon("nodes/publicField")

		/** 排序：先按所属类，再按名称，最后按定义位置。 */
		private val COMPARATOR: Comparator<JField> = compareBy(
			{ obj: JField -> obj.getJParent() },
			{ obj: JField -> obj.getName() },
			{ obj: JField -> obj.getPos() },
		)
	}
}
