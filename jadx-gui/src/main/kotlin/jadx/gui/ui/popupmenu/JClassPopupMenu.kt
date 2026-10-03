package jadx.gui.ui.popupmenu

import jadx.api.DecompilationMode
import jadx.core.dex.visitors.SaveCode
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.mode.JCodeMode
import jadx.gui.ui.dialog.RenameDialog
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.utils.NLS
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.Locale
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * 类节点的右键菜单。
 *
 * **做什么**：提供「重命名」菜单项，以及「导出」子菜单（Java / Smali / Simple / Fallback 四种代码）。
 * 导出时会弹出文件保存对话框，并根据 [JClassExportType.extension] 补全扩展名。
 *
 * **线程模型**：菜单动作在 EDT 上执行，导出为同步文件写入（与原实现一致）。
 */
class JClassPopupMenu(private val mainWindow: MainWindow, jClass: JClass) : JPopupMenu() {

	init {
		add(RenameDialog.buildRenamePopupMenuItem(mainWindow, jClass))
		add(makeExportSubMenu(jClass))
	}

	private fun makeExportSubMenu(jClass: JClass): JMenuItem {
		val exportSubMenu = JMenu(NLS.str("popup.export"))

		exportSubMenu.add(makeExportMenuItem(jClass, NLS.str("tabs.code"), JClassExportType.Code))
		exportSubMenu.add(makeExportMenuItem(jClass, NLS.str("tabs.smali"), JClassExportType.Smali))
		exportSubMenu.add(makeExportMenuItem(jClass, "Simple", JClassExportType.Simple))
		exportSubMenu.add(makeExportMenuItem(jClass, "Fallback", JClassExportType.Fallback))

		return exportSubMenu
	}

	/** 为指定的导出类型创建一个菜单项。 */
	fun makeExportMenuItem(jClass: JClass, label: String, exportType: JClassExportType): JMenuItem {
		val exportMenuItem = JMenuItem(label)
		exportMenuItem.addActionListener {
			val fileName = jClass.getName() + "." + exportType.extension

			val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.EXPORT_NODE)
			fileDialog.setFileExtList(listOf(exportType.extension))
			val currentDir: Path? = fileDialog.currentDir
			if (currentDir != null) {
				fileDialog.setSelectedFile(currentDir.resolve(fileName))
			}

			val selectedPaths = fileDialog.show()
			if (selectedPaths.size != 1) {
				return@addActionListener
			}

			val selectedPath = selectedPaths[0]
			val savePath: Path
			// Append file extension if missing
			if (!selectedPath.getFileName().toString().lowercase(Locale.ROOT).endsWith(exportType.extension)) {
				savePath = selectedPath.resolveSibling(selectedPath.getFileName().toString() + "." + exportType.extension)
			} else {
				savePath = selectedPath
			}

			saveJClass(jClass, savePath, exportType)

			LOG.info("Done saving {}", savePath)
		}

		return exportMenuItem
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(JClassPopupMenu::class.java)

		/** 保存类代码到指定路径（供包导出复用）。 */
		fun saveJClass(jClass: JClass, savePath: Path, exportType: JClassExportType) {
			SaveCode.save(getCode(jClass, exportType), savePath.toFile())
		}

		private fun getCode(jClass: JClass, exportType: JClassExportType): String = when (exportType) {
			JClassExportType.Code -> jClass.getCodeInfo().codeStr

			JClassExportType.Smali -> jClass.smali

			JClassExportType.Simple -> {
				val jClassSimple: JNode = JCodeMode(jClass, DecompilationMode.SIMPLE)
				jClassSimple.getCodeInfo().codeStr
			}

			JClassExportType.Fallback -> {
				val jClassFallback: JNode = JCodeMode(jClass, DecompilationMode.FALLBACK)
				jClassFallback.getCodeInfo().codeStr
			}
		}
	}
}
