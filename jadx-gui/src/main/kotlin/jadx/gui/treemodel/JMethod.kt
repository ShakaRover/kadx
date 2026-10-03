package jadx.gui.treemodel

import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.api.data.ICodeRename
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.api.metadata.ICodeNodeRef
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.args.ArgType
import jadx.gui.ui.MainWindow
import jadx.gui.ui.cellrenders.MethodRenderHelper
import jadx.gui.ui.dialog.RenameDialog
import jadx.gui.utils.UiUtils
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import java.util.Comparator
import javax.swing.Icon
import javax.swing.JPopupMenu

/**
 * 方法节点。
 *
 * **做什么**：在类树下展示一个 [JavaMethod]，支持重命名、跳转、tooltip 与排序。
 *
 * **为什么不是 `data class`**：节点相等性委托给 [mth]，且需要按身份参与树比较。
 */
class JMethod(
	private val mth: JavaMethod,
	private val jParent: JClass,
) : JNode(),
	JRenameNode {

	override fun getJavaNode(): JavaNode = mth

	val javaMethod: JavaMethod get() = mth

	override fun getCodeNodeRef(): ICodeNodeRef = mth.getMethodNode()

	override fun getJParent(): JClass = jParent

	val returnType: ArgType get() = mth.returnType

	override fun getRootClass(): JClass = jParent.getRootClass()

	override fun getIcon(): Icon = MethodRenderHelper.getIcon(mth)

	override fun getSyntaxName(): String = SyntaxConstants.SYNTAX_STYLE_JAVA

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu = RenameDialog.buildRenamePopup(mainWindow, this)

	private fun makeBaseString(): String = MethodRenderHelper.makeBaseString(mth)

	override fun getName(): String = mth.getName()

	override fun getTitle(): String = makeLongStringHtml()

	override fun canRename(): Boolean {
		if (mth.isClassInit()) {
			return false
		}
		return !mth.getMethodNode().contains(AFlag.DONT_RENAME)
	}

	override fun replace(): JRenameNode {
		if (mth.isConstructor()) {
			// 构造函数不能直接改名，改为重命名其所在类
			return jParent
		}
		return this
	}

	override fun buildCodeRename(newName: String, renames: MutableSet<ICodeRename>): ICodeRename {
		val relatedMethods = mth.getOverrideRelatedMethods()
		if (relatedMethods.isNotEmpty()) {
			for (relatedMethod in relatedMethods) {
				renames.remove(JadxCodeRename(JadxNodeRef.forMth(relatedMethod), ""))
			}
		}
		return JadxCodeRename(JadxNodeRef.forMth(mth), newName)
	}

	override fun isValidName(newName: String): Boolean = NameMapper.isValidIdentifier(newName)

	override fun removeAlias() {
		mth.removeAlias()
	}

	override fun addUpdateNodes(toUpdate: MutableList<JavaNode>) {
		toUpdate.add(mth)
		toUpdate.addAll(mth.useIn)
		val overrideRelatedMethods = mth.getOverrideRelatedMethods()
		toUpdate.addAll(overrideRelatedMethods)
		for (ovrdMth in overrideRelatedMethods) {
			toUpdate.addAll(ovrdMth.useIn)
		}
	}

	override fun reload(mainWindow: MainWindow) {
		mainWindow.reloadTreePreservingState()
	}

	override fun makeString(): String = UiUtils.typeFormat(makeBaseString(), returnType)

	override fun makeStringHtml(): String = UiUtils.typeFormatHtml(makeBaseString(), returnType)

	override fun makeLongString(): String {
		val name = mth.declaringClass.getFullName() + '.' + makeBaseString()
		return UiUtils.typeFormat(name, returnType)
	}

	override fun makeLongStringHtml(): String {
		val name = mth.declaringClass.getFullName() + '.' + makeBaseString()
		return UiUtils.typeFormatHtml(name, returnType)
	}

	override fun disableHtml(): Boolean = false

	override fun makeDescString(): String = UiUtils.typeStr(returnType) + " " + makeBaseString()

	override fun hasDescString(): Boolean = false

	override fun getPos(): Int = mth.getDefPos()

	override fun hashCode(): Int = mth.hashCode()

	override fun equals(other: Any?): Boolean = this === other || (other is JMethod && mth == other.mth)

	fun compareToMth(other: JMethod): Int = COMPARATOR.compare(this, other)

	override fun compareTo(other: JNode): Int {
		if (other is JMethod) {
			return compareToMth(other)
		}
		if (other is JClass) {
			val cmp = jParent.compareToCls(other)
			if (cmp != 0) {
				return cmp
			}
			return 1
		}
		return super.compareTo(other)
	}

	companion object {
		private const val serialVersionUID = 3834526867464663751L

		/** 排序：先按所属类，再按方法短签名，最后按定义位置。 */
		private val COMPARATOR: Comparator<JMethod> = compareBy(
			{ obj: JMethod -> obj.getJParent() },
			{ obj: JMethod -> obj.mth.getMethodNode().methodInfo.shortId },
			{ obj: JMethod -> obj.getPos() },
		)
	}
}
