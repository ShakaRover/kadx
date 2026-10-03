package jadx.gui.ui.filedialog

import jadx.gui.settings.JadxProject
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * 文件对话框的统一封装。
 *
 * **做什么**：根据 [FileOpenMode] 预置标题、扩展名过滤、选择模式与初始目录，
 * 再按设置选择使用 Swing 的 `JFileChooser` 还是原生 AWT `FileDialog`。
 *
 * **为什么保留显式 getter/setter 函数**：Java 调用方（`MainWindow`）与 Kotlin 调用方
 * 都按 `getCurrentDir()` / `setTitle(...)` 等函数名访问；用函数而非属性可保持签名零改动，
 * 因此内部字段改用不同名字以避免 JVM 上的 getter 命名冲突。
 */
class FileDialogWrapper(mainWindow: MainWindow, mode: FileOpenMode) {

	private val window: MainWindow = mainWindow

	private var open: Boolean = false
	private var dialogTitle: String? = null
	private var extList: List<String> = ArrayList()
	private var selMode: Int = JFileChooser.FILES_AND_DIRECTORIES
	private var dir: Path? = null
	private var selFile: Path? = null

	init {
		initForMode(mode)
	}

	fun setTitle(title: String) {
		dialogTitle = title
	}

	fun setFileExtList(fileExtList: List<String>) {
		extList = fileExtList
	}

	fun setSelectionMode(selectionMode: Int) {
		selMode = selectionMode
	}

	fun setSelectedFile(path: Path) {
		selFile = path
	}

	fun setCurrentDir(currentDir: Path) {
		dir = currentDir
	}

	fun show(): List<Path> = if (window.getSettings().isUseAlternativeFileDialog()) {
		CustomFileDialog(this).showDialog()
	} else {
		CustomFileChooser(this).showDialog()
	}

	private fun initForMode(mode: FileOpenMode) {
		when (mode) {
			FileOpenMode.OPEN_PROJECT -> {
				dialogTitle = NLS.str("file.open_title")
				extList = listOf(JadxProject.PROJECT_EXTENSION)
				selMode = JFileChooser.FILES_AND_DIRECTORIES
				dir = window.getSettings().getLastOpenFilePath()
				open = true
			}

			FileOpenMode.OPEN -> {
				dialogTitle = NLS.str("file.open_title")
				extList = ArrayList(OPEN_FILES_EXTS).apply {
					add(JadxProject.PROJECT_EXTENSION)
					add("aab")
				}
				selMode = JFileChooser.FILES_AND_DIRECTORIES
				dir = window.getSettings().getLastOpenFilePath()
				open = true
			}

			FileOpenMode.ADD -> {
				dialogTitle = NLS.str("file.add_files_action")
				extList = ArrayList(OPEN_FILES_EXTS).apply { add("aab") }
				selMode = JFileChooser.FILES_AND_DIRECTORIES
				dir = window.getSettings().getLastOpenFilePath()
				open = true
			}

			FileOpenMode.SAVE_PROJECT -> {
				dialogTitle = NLS.str("file.save_project")
				extList = listOf(JadxProject.PROJECT_EXTENSION)
				selMode = JFileChooser.FILES_ONLY
				dir = window.getSettings().getLastSaveFilePath()
				open = false
			}

			FileOpenMode.EXPORT -> {
				dialogTitle = NLS.str("file.save_all_msg")
				extList = emptyList()
				selMode = JFileChooser.DIRECTORIES_ONLY
				dir = window.getSettings().getLastSaveFilePath()
				open = false
			}

			FileOpenMode.CUSTOM_SAVE -> {
				open = false
				dir = window.getSettings().getLastSaveFilePath()
			}

			FileOpenMode.CUSTOM_OPEN -> {
				open = true
				dir = window.getSettings().getLastOpenFilePath()
			}

			FileOpenMode.EXPORT_NODE -> {
				open = false
				dialogTitle = NLS.str("file.export_node")
				dir = window.getSettings().getLastSaveFilePath()
				selMode = JFileChooser.FILES_ONLY
			}

			FileOpenMode.EXPORT_NODE_FOLDER -> {
				open = false
				dialogTitle = NLS.str("file.save_all_msg")
				dir = window.getSettings().getLastSaveFilePath()
				selMode = JFileChooser.DIRECTORIES_ONLY
			}
		}
	}

	fun getCurrentDir(): Path? = dir

	fun getMainWindow(): MainWindow = window

	fun isOpen(): Boolean = open

	fun getTitle(): String? = dialogTitle

	fun getFileExtList(): List<String> = extList

	fun getSelectionMode(): Int = selMode

	fun getSelectedFile(): Path? = selFile

	companion object {
		private val OPEN_FILES_EXTS: List<String> = listOf(
			"apk", "dex", "jar", "class", "smali", "zip", "aar", "arsc", "jadx.kts", "xapk", "apkm", "apks",
		)
	}
}
