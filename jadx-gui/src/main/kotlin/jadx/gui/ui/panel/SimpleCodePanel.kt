package jadx.gui.ui.panel

import jadx.api.ICodeInfo
import jadx.gui.settings.JadxSettings
import jadx.gui.settings.LineNumbersMode
import jadx.gui.treemodel.CodeNode
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.utils.NLS
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * 显示所选节点代码的简单面板（用于「用法」对话框等场景）。
 *
 * **做什么**：把节点的完整代码显示在只读代码区中，并尽力高亮/滚动到指定的代码行；
 * 若节点带有精确位置（[CodeNode]），则按字符偏移定位，否则退化为字符串匹配。
 *
 * **线程模型**：滚动定位通过 `SwingUtilities.invokeLater` 延迟到 EDT 执行，保持原模型。
 */
// The code panel class is used to display the code of the selected node.
class SimpleCodePanel(mainWindow: MainWindow) : JPanel() {

	private val codeArea: RSyntaxTextArea
	private val codeScrollPane: RTextScrollPane
	private val titleLabel: JLabel

	init {
		val settings: JadxSettings = mainWindow.getSettings()

		layout = BorderLayout(5, 5)
		border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

		// Set the minimum size to ensure the panel is not completely minimized
		minimumSize = Dimension(300, 400)
		preferredSize = Dimension(800, 600)

		// The title label
		titleLabel = JLabel(NLS.str("usage_dialog_plus.code_view"))
		titleLabel.setFont(settings.codeFont)
		titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5))

		// The code area
		codeArea = AbstractCodeArea.getDefaultArea(mainWindow)
		codeArea.setText("// " + NLS.str("usage_dialog_plus.select_node"))

		codeScrollPane = RTextScrollPane(codeArea)
		codeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
		codeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED)

		add(titleLabel, BorderLayout.NORTH)
		add(codeScrollPane, BorderLayout.CENTER)

		applySettings(settings)
	}

	private fun applySettings(settings: JadxSettings) {
		codeScrollPane.setLineNumbersEnabled(settings.lineNumbersMode != LineNumbersMode.DISABLE)
		codeScrollPane.getGutter().setLineNumberFont(settings.codeFont)
		codeArea.setFont(settings.codeFont)
	}

	/** 展示 [node] 的代码，并高亮 [codeLine] 所在行；[node] 为 `null` 时显示占位提示。 */
	fun showCode(node: JNode?, codeLine: String) {
		if (node == null) {
			titleLabel.setText(NLS.str("usage_dialog_plus.code_view"))
			codeArea.setText("// " + NLS.str("usage_dialog_plus.select_node"))
			return
		}
		titleLabel.setText(NLS.str("usage_dialog_plus.code_for", node.makeLongString()))
		codeArea.setSyntaxEditingStyle(node.getSyntaxName())

		// Get the complete code
		val contextCode = getContextCode(node, codeLine)
		codeArea.setText(contextCode)

		// Highlight the key line and scroll to that position
		scrollToCodeLine(codeArea, codeLine)

		// If it is a CodeNode, we can get a more precise position
		if (node !is CodeNode) {
			// Not a CodeNode, use string matching
			scrollToCodeLine(codeArea, codeLine)
			return
		}
		val pos = node.getPos()
		if (pos <= 0) {
			// If there is no position information, use string matching
			scrollToCodeLine(codeArea, codeLine)
			return
		}
		// Try to use the position information to more accurately locate
		try {
			val text = codeArea.getText()
			var lineNum = 0
			var curPos = 0
			// Calculate the line number corresponding to the position
			var i = 0
			while (i < text.length && curPos <= pos) {
				if (text[i] == '\n') {
					lineNum++
				}
				curPos++
				i++
			}

			if (lineNum > 0) {
				// Scroll to the calculated line number
				val finalLineNum = lineNum
				SwingUtilities.invokeLater {
					try {
						val lineRect = codeArea.modelToView2D(codeArea.getLineStartOffset(finalLineNum))
						if (lineRect != null) {
							val scrollPane = codeArea.getParent().getParent() as JScrollPane
							val viewRect = scrollPane.getViewport().getViewRect()
							var y = (lineRect.getY() - (viewRect.height - lineRect.getHeight()) / 2).toInt()
							if (y < 0) {
								y = 0
							}
							scrollPane.getViewport().setViewPosition(Point(0, y))
						}
					} catch (e: Exception) {
						// Fall back to using string matching
						scrollToCodeLine(codeArea, codeLine)
					}
				}
			}
		} catch (e: Exception) {
			// Fall back to using string matching
			scrollToCodeLine(codeArea, codeLine)
		}
	}

	private fun getContextCode(node: JNode, codeLine: String): String {
		// Always try to get the complete code
		if (node is CodeNode) {
			val usageJNode = node.getJParent()
			if (usageJNode != null) {
				// Try to get the complete code of the method or class
				val fullCode = getFullNodeCode(usageJNode)
				if (fullCode != null && fullCode.isNotEmpty()) {
					return fullCode
				}
			}
		}

		// If you cannot get more context, at least add some empty lines and comments
		return "// Unable to get complete context, only display related lines\n\n" + codeLine
	}

	private fun getFullNodeCode(node: JNode?): String? {
		if (node != null) {
			// Get the code information of the node
			val codeInfo: ICodeInfo = node.getCodeInfo()
			if (codeInfo !== ICodeInfo.EMPTY) {
				return codeInfo.codeStr
			}

			// If it is a class node, try to get the class code
			if (node is JClass) {
				return node.getCodeInfo().codeStr
			}
		}
		return null
	}

	private fun scrollToCodeLine(textArea: RSyntaxTextArea, lineToHighlight: String) {
		// Try to find and highlight a specific line in the code and scroll to that position
		try {
			val fullText = textArea.getText()
			val lineIndex = fullText.indexOf(lineToHighlight)
			if (lineIndex >= 0) {
				// Ensure the text area has updated the layout
				textArea.revalidate()

				// Highlight the code line
				textArea.setCaretPosition(lineIndex)
				val endIndex = lineIndex + lineToHighlight.length
				textArea.select(lineIndex, endIndex)
				textArea.getCaret().setSelectionVisible(true)

				// Use SwingUtilities.invokeLater to ensure the scroll is executed after the UI is updated
				SwingUtilities.invokeLater {
					try {
						// Get the line number
						val lineNum = textArea.getLineOfOffset(lineIndex)
						// Ensure the line is centered in the view
						val lineRect: Rectangle2D? = textArea.modelToView2D(textArea.getLineStartOffset(lineNum))
						if (lineRect != null) {
							// Calculate the center point of the view
							val scrollPane = textArea.getParent().getParent() as JScrollPane
							val viewRect: Rectangle = scrollPane.getViewport().getViewRect()
							var y = (lineRect.getY() - (viewRect.height - lineRect.getHeight()) / 2).toInt()
							if (y < 0) {
								y = 0
							}
							// Scroll to the calculated position
							scrollPane.getViewport().setViewPosition(Point(0, y))
						}
					} catch (e: Exception) {
						LOG.debug("Error scrolling to line: {}", e.message)
					}
				}
			} else {
				LOG.debug("Could not find line to highlight: {}", lineToHighlight)
			}
		} catch (e: Exception) {
			LOG.debug("Error highlighting line: {}", e.message)
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(SimpleCodePanel::class.java)
	}
}
