package jadx.gui.ui.filedialog

import java.io.File
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * 支持多段扩展名的文件过滤器。
 *
 * **做什么**：克服 [FileNameExtensionFilter] 只把“最后一个点之后”当作扩展名的限制，
 * 从而支持 `jadx.kts` 这类多段扩展名。
 *
 * **为什么委托给 [FileNameExtensionFilter]**：描述文本的格式化（含扩展名列表）
 * 直接复用 Swing 的实现，避免自造格式。
 */
internal class FileNameMultiExtensionFilter(description: String, vararg extensions: String) : FileFilter() {
	private val delegate: FileNameExtensionFilter = FileNameExtensionFilter(description, extensions[0])
	private val extensions: Array<out String> = extensions

	override fun accept(file: File): Boolean {
		if (file.isDirectory()) {
			return true
		}
		val fileName = file.name
		for (extension in extensions) {
			if (fileName.endsWith(extension)) {
				return true
			}
		}
		return false
	}

	override fun getDescription(): String = delegate.description
}
