package kadx.gui.ui.filedialog

import kadx.gui.settings.KadxProject
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
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

	fun show(): List<Path> = if (window.getSettings().isUseAlternativeFileDialog) {
		CustomFileDialog(this).showDialog()
	} else {
		CustomFileChooser(this).showDialog()
	}

	private fun initForMode(mode: FileOpenMode) {
		when (mode) {
			FileOpenMode.OPEN_PROJECT -> {
				dialogTitle = NLS.str("file.open_title")
				extList = listOf(KadxProject.PROJECT_EXTENSION)
				selMode = JFileChooser.FILES_AND_DIRECTORIES
				dir = window.getSettings().lastOpenFilePath
				open = true
			}

			FileOpenMode.OPEN -> {
				dialogTitle = NLS.str("file.open_title")
				extList = ArrayList(OPEN_FILES_EXTS).apply {
					add(KadxProject.PROJECT_EXTENSION)
					add("aab")
				}
				selMode = JFileChooser.FILES_AND_DIRECTORIES
				dir = window.getSettings().lastOpenFilePath
				open = true
			}

			FileOpenMode.ADD -> {
				dialogTitle = NLS.str("file.add_files_action")
				extList = ArrayList(OPEN_FILES_EXTS).apply { add("aab") }
				selMode = JFileChooser.FILES_AND_DIRECTORIES
				dir = window.getSettings().lastOpenFilePath
				open = true
			}

			FileOpenMode.SAVE_PROJECT -> {
				dialogTitle = NLS.str("file.save_project")
				extList = listOf(KadxProject.PROJECT_EXTENSION)
				selMode = JFileChooser.FILES_ONLY
				dir = window.getSettings().lastSaveFilePath
				open = false
			}

			FileOpenMode.EXPORT -> {
				dialogTitle = NLS.str("file.save_all_msg")
				extList = emptyList()
				selMode = JFileChooser.DIRECTORIES_ONLY
				dir = window.getSettings().lastSaveFilePath
				open = false
			}

			FileOpenMode.CUSTOM_SAVE -> {
				open = false
				dir = window.getSettings().lastSaveFilePath
			}

			FileOpenMode.CUSTOM_OPEN -> {
				open = true
				dir = window.getSettings().lastOpenFilePath
			}

			FileOpenMode.EXPORT_NODE -> {
				open = false
				dialogTitle = NLS.str("file.export_node")
				dir = window.getSettings().lastSaveFilePath
				selMode = JFileChooser.FILES_ONLY
			}

			FileOpenMode.EXPORT_NODE_FOLDER -> {
				open = false
				dialogTitle = NLS.str("file.save_all_msg")
				dir = window.getSettings().lastSaveFilePath
				selMode = JFileChooser.DIRECTORIES_ONLY
			}
		}
	}

	val currentDir: Path? get() = dir

	val mainWindow: MainWindow get() = window

	val isOpen: Boolean get() = open

	val title: String? get() = dialogTitle

	val fileExtList: List<String> get() = extList

	val selectionMode: Int get() = selMode

	val selectedFile: Path? get() = selFile

	companion object {
		private val OPEN_FILES_EXTS: List<String> = listOf(
			"apk", "dex", "jar", "class", "smali", "zip", "aar", "arsc", "kadx.kts", "xapk", "apkm", "apks",
		)
	}
}
