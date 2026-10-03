package jadx.gui.utils.pkgs

import jadx.api.JavaNode
import jadx.api.JavaPackage
import jadx.api.data.ICodeRename
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.core.deobf.NameMapper
import jadx.gui.treemodel.JRenameNode
import jadx.gui.ui.MainWindow
import jadx.gui.utils.Icons
import org.apache.commons.lang3.StringUtils
import java.util.regex.Pattern
import javax.swing.Icon

/**
 * 包（package）重命名节点。
 *
 * **做什么**：把 [JavaPackage] 适配成 [JRenameNode]，参与重命名对话框：
 * 生成重命名记录、校验新包名、刷新包树。
 *
 * **为什么不是 `data class`**：它以身份参与树节点比较，且持有可变引用。
 */
class JRenamePackage(
	private val refPkg: JavaPackage,
	private val rawFullName: String,
	private val fullName: String,
	private val name: String,
) : JRenameNode {

	override fun getJavaNode(): JavaNode = refPkg

	override fun getTitle(): String = fullName

	override fun getName(): String = name

	override fun getIcon(): Icon = Icons.PACKAGE

	override fun canRename(): Boolean = true

	override fun buildCodeRename(newName: String, renames: MutableSet<ICodeRename>): ICodeRename = JadxCodeRename(JadxNodeRef.forPkg(rawFullName), newName)

	override fun isValidName(newName: String): Boolean = isValidPackageName(newName)

	override fun removeAlias() {
		refPkg.removeAlias()
	}

	override fun addUpdateNodes(toUpdate: MutableList<JavaNode>) {
		refPkg.addUseIn(toUpdate)
	}

	override fun reload(mainWindow: MainWindow) {
		mainWindow.rebuildPackagesTree()
		mainWindow.reloadTreePreservingState()
	}

	override fun toString(): String = refPkg.toString()

	companion object {
		/** 合法的包重命名模式：由若干段合法 Java 标识符组成，允许前导点（相对包名）。 */
		private val PACKAGE_RENAME_PATTERN: Pattern =
			Pattern.compile("(\\.)?PKG(\\.PKG)*".replace("PKG", NameMapper.VALID_JAVA_IDENTIFIER.pattern()))

		/** 校验新包名是否合法（非空、非保留字、逐段均为合法标识符）。 */
		fun isValidPackageName(newName: String?): Boolean {
			if (newName == null || newName.isEmpty() || NameMapper.isReserved(newName)) {
				return false
			}
			val matcher = PACKAGE_RENAME_PATTERN.matcher(newName)
			if (!matcher.matches()) {
				return false
			}
			for (part in StringUtils.split(newName, '.')) {
				if (NameMapper.isReserved(part)) {
					return false
				}
			}
			return true
		}
	}
}
