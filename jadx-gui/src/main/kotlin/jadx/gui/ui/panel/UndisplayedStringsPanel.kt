package jadx.gui.ui.panel

import jadx.gui.settings.JadxSettings
import jadx.gui.settings.LineNumbersMode
import jadx.gui.settings.ui.font.FontChooserHack
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.ui.treenodes.UndisplayedStringsNode
import org.drjekyll.fontchooser.FontChooser
import org.drjekyll.fontchooser.model.FontSelectionModel
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory

/**
 * 「未显示的字符串」面板。
 *
 * **做什么**：展示 `UndisplayedStringsNode` 收集到的、未被引用的字符串，
 * 右侧提供一个字体选择器，实时修改代码区字体并持久化到设置。
 *
 * **线程模型**：字体选择回调在 EDT 上执行，直接修改设置并刷新主窗口。
 */
class UndisplayedStringsPanel(panel: TabbedPane, node: UndisplayedStringsNode) : ContentPanel(panel, node) {

	private val textPane: RSyntaxTextArea
	private val codeScrollPane: RTextScrollPane

	init {
		layout = BorderLayout()
		textPane = AbstractCodeArea.getDefaultArea(panel.getMainWindow())

		val settings: JadxSettings = getSettings()
		val selectedFont: Font = settings.getCodeFont()

		val fontChooser = FontChooser()
		fontChooser.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5))
		fontChooser.setSelectedFont(selectedFont)
		FontChooserHack.hidePreview(fontChooser)

		fontChooser.addChangeListener { event ->
			val model = event.getSource() as FontSelectionModel
			settings.setCodeFont(model.getSelectedFont())
			getMainWindow().loadSettings()
		}

		codeScrollPane = RTextScrollPane(textPane)

		add(codeScrollPane, BorderLayout.CENTER)
		add(fontChooser, BorderLayout.EAST)

		applySettings()
		showData(node.makeDescString())
	}

	private fun applySettings() {
		codeScrollPane.setLineNumbersEnabled(getSettings().getLineNumbersMode() != LineNumbersMode.DISABLE)
		codeScrollPane.getGutter().setLineNumberFont(getSettings().getCodeFont())
		textPane.setFont(getSettings().getCodeFont())
	}

	/** 展示文本内容；[data] 允许为 `null`（此时等价于清空）。 */
	private fun showData(data: String?) {
		textPane.setText(data)
		textPane.setCaretPosition(0)
	}

	override fun loadSettings() {
		applySettings()
	}
}
