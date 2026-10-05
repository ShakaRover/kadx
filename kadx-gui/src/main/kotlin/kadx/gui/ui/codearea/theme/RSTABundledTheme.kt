package kadx.gui.ui.codearea.theme

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import org.slf4j.LoggerFactory

/**
 * 读取 RSyntaxTextArea 自带（bundled）的 XML 主题。
 *
 * **做什么**：从 classpath 的 `/org/fife/ui/rsyntaxtextarea/themes/<name>.xml`
 * 加载主题；如果资源不存在或解析失败，就退回默认主题，保证界面不崩。
 */
class RSTABundledTheme(private val themeName: String) : IEditorTheme {

	private var loadedTheme: Theme? = null

	override val id: String get() = "RSTA:$themeName"

	override val name: String get() = themeName

	override fun load() {
		val path = RSTA_THEME_PATH + themeName + ".xml"
		try {
			RSTABundledTheme::class.java.getResourceAsStream(path).use { inputStream ->
				// 资源缺失时 inputStream 为 null，Theme.load 会抛异常，由下方 catch 兜底
				loadedTheme = Theme.load(inputStream)
			}
		} catch (t: Throwable) {
			LOG.error("Failed to load editor theme: {}", path, t)
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
		private val LOG = LoggerFactory.getLogger(RSTABundledTheme::class.java)
		private const val RSTA_THEME_PATH = "/org/fife/ui/rsyntaxtextarea/themes/"
	}
}
