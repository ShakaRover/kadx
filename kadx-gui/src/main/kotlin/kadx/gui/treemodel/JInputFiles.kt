package kadx.gui.treemodel

import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.gui.utils.ui.SimpleMenuItem
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

/**
 * 普通输入文件列表节点。
 *
 * **做什么**：把多个输入文件包成一组；`.smali` 文件会生成可编辑的 [JInputSmaliFile]，
 * 其余生成 [JInputFile]。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class JInputFiles(files: List<Path>) : JNode() {

	init {
		for (file in files) {
			val fileName = file.fileName.toString()
			if (fileName.endsWith(".smali")) {
				add(JInputSmaliFile(file))
			} else {
				add(JInputFile(file))
			}
		}
	}

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu {
		val menu = JPopupMenu()
		menu.add(SimpleMenuItem(NLS.str("popup.add_files"), Runnable { mainWindow.addFiles() }))
		return menu
	}

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon = INPUT_FILES_ICON

	override fun getID(): String = "JInputFiles"

	override fun makeString(): String = NLS.str("tree.input_files")

	companion object {
		private val INPUT_FILES_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/moduleDirectory")
	}
}
