package jadx.gui.ui.filedialog

import jadx.core.utils.ListUtils
import jadx.core.utils.Utils
import jadx.core.utils.files.FileUtils
import java.awt.FileDialog
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 使用 AWT [FileDialog] 的“替代”文件对话框实现。
 *
 * **做什么**：当设置里启用“使用替代文件对话框”时，用原生 AWT 对话框代替 Swing 的
 * `JFileChooser`；支持多选、按扩展名过滤。
 */
internal class CustomFileDialog(private val data: FileDialogWrapper) {

	fun showDialog(): List<Path> {
		val fileDialog = FileDialog(data.mainWindow, data.title)
		fileDialog.mode = if (data.isOpen) FileDialog.LOAD else FileDialog.SAVE
		fileDialog.isMultipleMode = true
		val fileExtList = data.fileExtList
		if (Utils.notEmpty(fileExtList)) {
			fileDialog.setFilenameFilter { _, name -> ListUtils.anyMatch(fileExtList) { name.endsWith(it) } }
		}
		data.selectedFile?.let { fileDialog.file = it.toAbsolutePath().toString() }
		data.currentDir?.let { fileDialog.directory = it.toAbsolutePath().toString() }
		fileDialog.isVisible = true
		val selectedFiles = fileDialog.files
		if (!Utils.isEmpty(selectedFiles)) {
			data.setCurrentDir(Paths.get(fileDialog.directory))
			return FileUtils.toPathsWithTrim(selectedFiles)
		}
		val chosenFile = fileDialog.file
		if (chosenFile != null) {
			return listOf(FileUtils.toPathWithTrim(chosenFile))
		}
		return emptyList()
	}
}
