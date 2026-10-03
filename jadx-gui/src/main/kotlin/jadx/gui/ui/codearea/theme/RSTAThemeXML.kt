package jadx.gui.ui.codearea.theme

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * 从磁盘上的 XML 文件加载 RSyntaxTextArea 主题。
 *
 * **做什么**：读取用户指定的 `.xml` 主题文件；解析失败时退回默认主题。
 */
class RSTAThemeXML(private val themePath: Path, override val name: String) : IEditorTheme {

	private var loadedTheme: Theme? = null

	override val id: String get() = "file:$themePath"

	override fun load() {
		try {
			Files.newInputStream(themePath).use { inputStream ->
				loadedTheme = Theme.load(inputStream)
			}
		} catch (e: Exception) {
			LOG.warn("Failed to load editor theme: {}", themePath, e)
			loadedTheme = Theme(RSyntaxTextArea())
		}
	}

	override fun apply(textArea: RSyntaxTextArea) {
		// 正常情况下 apply 之前一定调用过 load；用 checkNotNull 代替裸 `!!`
		checkNotNull(loadedTheme).apply(textArea)
	}

	override fun unload() {
		loadedTheme = null
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(RSTAThemeXML::class.java)
	}
}
