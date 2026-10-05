package kadx.gui.ui.filedialog

/**
 * 文件对话框的打开模式。
 *
 * **做什么**：决定标题、扩展名过滤、选择模式（文件/目录）以及是“打开”还是“保存”。
 * 各模式的具体初始化逻辑见 `FileDialogWrapper.initForMode`。
 */
enum class FileOpenMode {
	OPEN,
	OPEN_PROJECT,
	ADD,
	SAVE_PROJECT,
	EXPORT,
	CUSTOM_SAVE,
	CUSTOM_OPEN,
	EXPORT_NODE,
	EXPORT_NODE_FOLDER,
}
