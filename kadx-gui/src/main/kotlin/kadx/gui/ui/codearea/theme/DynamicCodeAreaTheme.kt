package kadx.gui.ui.codearea.theme

import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rtextarea.Gutter
import java.awt.Color
import javax.swing.UIManager

/**
 * 动态代码区主题：根据当前 Swing UI 主题（浅色 / 深色）实时混合出一套配色。
 *
 * **做什么**：从 [UIManager] 读取面板背景、前景、分隔线等颜色，判断是深色还是浅色，
 * 再为 RSyntaxTextArea 的每种 [Token] 类型设置前景色，最后设置背景、光标、
 * 选区、当前行高亮、括号匹配等颜色，并同步更新行号栏（[Gutter]）。
 *
 * **为什么是「动态」**：不写死主题文件，而是跟随用户切换的 LookAndFeel 自动适配。
 */
class DynamicCodeAreaTheme : IEditorTheme {

	override val id: String get() = "DynamicCodeAreaTheme"

	override val name: String get() = NLS.str("preferences.dynamic_editor_theme")

	override fun apply(textArea: RSyntaxTextArea) {
		// 从 UIManager 读取当前 UI 主题的颜色
		val themeBackground = UIManager.getColor("Panel.background")
		val themeForeground = UIManager.getColor("Panel.foreground")
		val separatorForeground = UIManager.getColor("Separator.foreground")
		val editorSelectionBackground = UIManager.getColor("EditorPane.selectionBackground")
		val caretForeground = UIManager.getColor("EditorPane.caretForeground")

		val scheme = textArea.getSyntaxScheme()

		val isDarkTheme = UiUtils.isDarkTheme(themeBackground)

		// 背景色随主题变化：深色主题用面板背景，浅色主题用纯白
		val editorBackground = if (isDarkTheme) themeBackground else Color.WHITE
		val lineHighlight = if (isDarkTheme) {
			UiUtils.adjustBrightness(themeBackground, 1.2f)
		} else {
			Color.decode("#EBECF0") // 浅色主题下的浅灰
		}
		val lineNumberForeground = UIManager.getColor("Label.foreground")

		// 选区颜色：深色用半透明蓝，浅色用更淡的蓝
		val selectionColor = if (isDarkTheme) {
			Color(51, 153, 255, 90)
		} else {
			Color(51, 153, 255, 50)
		}

		val markAllHighlightColor = if (isDarkTheme) Color.decode("#32593D") else Color.decode("#ffc800")
		val matchedBracketBackground = if (isDarkTheme) {
			UiUtils.adjustBrightness(Color.decode("#3B514D"), 1.2f)
		} else {
			Color.decode("#93D9D9")
		}
		val markOccurrencesColor = UiUtils.adjustBrightness(editorSelectionBackground, if (isDarkTheme) 0.6f else 1.4f)

		// 设置各语法 token 的前景色
		if (isDarkTheme) {
			val dataTypeColor = Color.decode("#4EC9B0")
			scheme.getStyle(Token.COMMENT_EOL).foreground = Color.decode("#57A64A")
			scheme.getStyle(Token.COMMENT_MULTILINE).foreground = Color.decode("#57A64A")
			scheme.getStyle(Token.COMMENT_DOCUMENTATION).foreground = Color.decode("#57A64A")
			scheme.getStyle(Token.COMMENT_KEYWORD).foreground = Color.decode("#57A64A")
			scheme.getStyle(Token.COMMENT_MARKUP).foreground = Color.decode("#57A64A")
			scheme.getStyle(Token.RESERVED_WORD).foreground = Color.decode("#569CD6")
			scheme.getStyle(Token.RESERVED_WORD_2).foreground = dataTypeColor
			scheme.getStyle(Token.FUNCTION).foreground = Color.decode("#DCDCAA")
			scheme.getStyle(Token.ANNOTATION).foreground = Color.decode("#B3AE60")
			scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = Color.decode("#D7BA7D")
			scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = Color.decode("#D7BA7D")
			scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL).foreground = Color.decode("#D7BA7D")
			scheme.getStyle(Token.LITERAL_BOOLEAN).foreground = Color.decode("#569CD6")
			scheme.getStyle(Token.LITERAL_CHAR).foreground = Color.decode("#CE9178")
			scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = Color.decode("#CE9178")
			scheme.getStyle(Token.DATA_TYPE).foreground = dataTypeColor
			scheme.getStyle(Token.OPERATOR).foreground = Color.WHITE
			scheme.getStyle(Token.SEPARATOR).foreground = Color.WHITE
			scheme.getStyle(Token.IDENTIFIER).foreground = themeForeground
			// XML 专用配色（深色）
			scheme.getStyle(Token.MARKUP_TAG_DELIMITER).foreground = Color.decode("#808080")
			scheme.getStyle(Token.MARKUP_TAG_NAME).foreground = Color.decode("#569CD6")
			scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE).foreground = Color.decode("#9CDCFE")
			scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE_VALUE).foreground = Color.decode("#CE9178")
		} else {
			val dataTypeColor = Color.decode("#267F99")
			scheme.getStyle(Token.COMMENT_EOL).foreground = Color.decode("#008000")
			scheme.getStyle(Token.COMMENT_MULTILINE).foreground = Color.decode("#008000")
			scheme.getStyle(Token.COMMENT_DOCUMENTATION).foreground = Color.decode("#008000")
			scheme.getStyle(Token.COMMENT_KEYWORD).foreground = Color.decode("#008000")
			scheme.getStyle(Token.COMMENT_MARKUP).foreground = Color.decode("#008000")
			scheme.getStyle(Token.RESERVED_WORD).foreground = Color.decode("#0000FF")
			scheme.getStyle(Token.RESERVED_WORD_2).foreground = dataTypeColor
			scheme.getStyle(Token.FUNCTION).foreground = Color.decode("#795E26")
			scheme.getStyle(Token.ANNOTATION).foreground = Color.decode("#9E8809")
			scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = Color.decode("#098658")
			scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = Color.decode("#098658")
			scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL).foreground = Color.decode("#098658")
			scheme.getStyle(Token.LITERAL_BOOLEAN).foreground = Color.decode("#0451A5")
			scheme.getStyle(Token.LITERAL_CHAR).foreground = Color.decode("#067d17")
			scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = Color.decode("#067d17")
			scheme.getStyle(Token.DATA_TYPE).foreground = dataTypeColor
			scheme.getStyle(Token.OPERATOR).foreground = Color.decode("#333333")
			scheme.getStyle(Token.SEPARATOR).foreground = Color.decode("#333333")
			scheme.getStyle(Token.IDENTIFIER).foreground = themeForeground
			// XML 专用配色（浅色）
			scheme.getStyle(Token.MARKUP_TAG_DELIMITER).foreground = Color.decode("#800000")
			scheme.getStyle(Token.MARKUP_TAG_NAME).foreground = Color.decode("#4A7A4F")
			scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE).foreground = Color.decode("#FF0000")
			scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE_VALUE).foreground = Color.decode("#0000FF")
		}

		textArea.setBackground(editorBackground)
		textArea.setCaretColor(caretForeground)
		textArea.setSelectionColor(selectionColor)
		textArea.setCurrentLineHighlightColor(lineHighlight)
		textArea.setMarkAllHighlightColor(markAllHighlightColor)
		textArea.setMarkOccurrencesColor(markOccurrencesColor)
		textArea.setHyperlinkForeground(editorSelectionBackground)
		textArea.setMatchedBracketBGColor(matchedBracketBackground)
		textArea.setMatchedBracketBorderColor(lineNumberForeground)

		textArea.setPaintMatchedBracketPair(true)
		textArea.setAnimateBracketMatching(false)
		textArea.setFadeCurrentLineHighlight(true)

		// 直接重置行号栏颜色，确保切换主题后立即生效
		val gutter: Gutter? = RSyntaxUtilities.getGutter(textArea)
		if (gutter != null) {
			gutter.setBackground(editorBackground)
			gutter.setBorderColor(separatorForeground)
			gutter.setLineNumberColor(lineNumberForeground)
		}
	}
}
