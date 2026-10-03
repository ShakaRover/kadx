package jadx.gui.treemodel

import jadx.gui.ui.MainWindow
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import jadx.gui.utils.ui.SimpleMenuItem
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPopupMenu

/**
 * 单个输入文件节点。
 *
 * **做什么**：展示一个输入文件（apk / dex / jar 等），右键可添加、移除或重命名输入。
 *
 * **为什么不是 `data class`**：相等性由文件路径决定，且需要按身份参与树比较。
 */
class JInputFile(private val filePath: Path) : JNode() {

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu = buildInputFilePopupMenu(mainWindow, filePath)

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon = Icons.FILE

	override fun makeString(): String = filePath.fileName.toString()

	override fun getTooltip(): String = filePath.normalize().toAbsolutePath().toString()

	override fun hashCode(): Int = filePath.hashCode()

	override fun equals(other: Any?): Boolean {
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		return (other as JInputFile).filePath == filePath
	}

	override fun toString(): String = "JInputFile{" + filePath + '}'

	companion object {
		/** 构建输入文件节点的右键菜单（[JInputSmaliFile] 也复用）。 */
		@JvmStatic
		fun buildInputFilePopupMenu(mainWindow: MainWindow, filePath: Path): JPopupMenu {
			val menu = JPopupMenu()
			menu.add(SimpleMenuItem(NLS.str("popup.add_files"), Runnable { mainWindow.addFiles() }))
			menu.add(SimpleMenuItem(NLS.str("popup.remove"), Runnable { mainWindow.removeInput(filePath) }))
			menu.add(SimpleMenuItem(NLS.str("popup.rename"), Runnable { mainWindow.renameInput(filePath) }))
			return menu
		}
	}
}
