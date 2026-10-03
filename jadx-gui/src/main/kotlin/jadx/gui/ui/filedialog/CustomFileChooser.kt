package jadx.gui.ui.filedialog

import jadx.api.plugins.utils.CommonFileUtils
import jadx.core.utils.StringUtils
import jadx.core.utils.Utils
import jadx.core.utils.files.FileUtils
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import java.awt.Component
import java.awt.Container
import java.awt.HeadlessException
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.UIManager
import javax.swing.text.DefaultEditorKit
import javax.swing.text.JTextComponent

/**
 * 基于 Swing [JFileChooser] 的文件对话框实现。
 *
 * **做什么**：设置标题/过滤/多选，处理“文件与目录”模式的确认逻辑，
 * 并给文件列表安装“从剪贴板粘贴文件路径”的增强操作。
 *
 * **为什么保留 Swing 线程模型**：所有逻辑仍在 EDT 上执行，与原来一致。
 */
internal class CustomFileChooser(private val data: FileDialogWrapper) : JFileChooser(data.currentDir?.toFile() ?: CommonFileUtils.CWD) {

	init {
		putClientProperty("FileChooser.useShellFolder", java.lang.Boolean.FALSE)
	}

	fun showDialog(): List<Path> {
		toolTipText = data.title
		fileSelectionMode = data.selectionMode
		isMultiSelectionEnabled = data.isOpen
		isAcceptAllFileFilterUsed = true
		val fileExtList = data.fileExtList
		if (Utils.notEmpty(fileExtList)) {
			val validFileExtList = fileExtList
				.filter { StringUtils.notBlank(it) }
			if (Utils.notEmpty(validFileExtList)) {
				val description = NLS.str("file_dialog.supported_files") + ": (" + Utils.listToString(validFileExtList) + ')'
				fileFilter = FileNameMultiExtensionFilter(description, *validFileExtList.toTypedArray())
			}
		}
		data.selectedFile?.let { selectedFile = it.toFile() }
		if (data.isOpen) {
			installFileListPasteAction(this)
		}
		val mainWindow = data.mainWindow
		val ret = if (data.isOpen) showOpenDialog(mainWindow) else showSaveDialog(mainWindow)
		if (ret != JFileChooser.APPROVE_OPTION) {
			return emptyList()
		}
		data.setCurrentDir(currentDirectory.toPath())
		val chosenFiles = selectedFiles
		if (chosenFiles.isNotEmpty()) {
			return FileUtils.toPathsWithTrim(chosenFiles)
		}
		val chosenFile = selectedFile
		if (chosenFile != null) {
			return listOf(FileUtils.toPathWithTrim(chosenFile))
		}
		return emptyList()
	}

	@Throws(HeadlessException::class)
	override fun createDialog(parent: Component): JDialog {
		val dialog = super.createDialog(parent)
		dialog.title = data.title
		dialog.setLocationRelativeTo(null)
		data.mainWindow.getSettings().loadWindowPos(dialog)
		dialog.addWindowListener(object : WindowAdapter() {
			override fun windowClosed(e: WindowEvent) {
				data.mainWindow.getSettings().saveWindowPos(dialog)
				super.windowClosed(e)
			}
		})
		return dialog
	}

	override fun approveSelection() {
		if (data.selectionMode == JFileChooser.FILES_AND_DIRECTORIES) {
			val currentFile = selectedFile
			if (currentFile != null && currentFile.isDirectory) {
				val option = JOptionPane.showConfirmDialog(
					data.mainWindow,
					NLS.str("file_dialog.load_dir_confirm") + "\n " + currentFile,
					NLS.str("file_dialog.load_dir_title"),
					JOptionPane.YES_NO_OPTION,
				)
				if (option != JOptionPane.YES_OPTION) {
					currentDirectory = currentFile
					updateUI()
					return
				}
			}
		}
		super.approveSelection()
	}

	private fun installFileListPasteAction(component: Component) {
		if (component is JTextComponent) {
			val defaultPasteAction: Action? = component.actionMap.get(DefaultEditorKit.pasteAction)
			component.actionMap.put(
				DefaultEditorKit.pasteAction,
				object : AbstractAction() {
					override fun actionPerformed(e: ActionEvent) {
						if (!pasteFileListFromClipboard(component)) {
							if (defaultPasteAction != null) {
								defaultPasteAction.actionPerformed(e)
							} else {
								component.paste()
							}
						}
					}
				},
			)
		}
		if (component is Container) {
			for (child in component.components) {
				installFileListPasteAction(child)
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun pasteFileListFromClipboard(textComponent: JTextComponent): Boolean {
		return try {
			val contents: Transferable? = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
			if (contents == null || !contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				return false
			}
			val clipboardFiles = contents.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
			val paths = clipboardFiles.filterNotNull()
				.joinToString(" ") { file -> '"' + file.absolutePath + '"' }
			if (paths.isEmpty()) {
				return false
			}
			textComponent.replaceSelection(paths)
			true
		} catch (e: Exception) {
			false
		}
	}

	companion object {
		init {
			// 禁用左侧快捷面板，否则某些 Windows 环境下会在枚举网络位置时崩溃
			UIManager.put("FileChooser.noPlacesBar", java.lang.Boolean.TRUE)
		}
	}
}
