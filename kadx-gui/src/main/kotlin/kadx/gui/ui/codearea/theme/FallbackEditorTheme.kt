package kadx.gui.ui.codearea.theme

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme

/**
 * 兜底主题：用「默认的 [RSyntaxTextArea]」构造一个基础 [Theme]。
 *
 * **做什么**：当没有配置任何主题、或配置的主题加载失败时使用，保证代码区总有配色，
 * 不会因为取不到主题而报错。
 */
class FallbackEditorTheme : IEditorTheme {

	private lateinit var baseTheme: Theme

	override val id: String get() = "fallback"

	override val name: String get() = "Fallback"

	override fun load() {
		baseTheme = Theme(RSyntaxTextArea())
	}

	override fun apply(textArea: RSyntaxTextArea) {
		baseTheme.apply(textArea)
	}
}
