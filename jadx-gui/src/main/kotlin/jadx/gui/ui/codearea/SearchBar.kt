package jadx.gui.ui.codearea

import com.formdev.flatlaf.FlatClientProperties
import jadx.core.utils.StringUtils
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import jadx.gui.utils.TextStandardActions
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.SearchContext
import org.fife.ui.rtextarea.SearchEngine
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.border.EmptyBorder
import javax.swing.text.BadLocationException

/**
 * 代码区顶部的查找工具条。
 *
 * **做什么**：提供查找输入框、结果计数、大小写/全词/正则/全部标记开关，
 * 以及上一个/下一个/关闭按钮。行为尽量贴近 IntelliJ 的查找栏：
 * 若有选中文本则以其为搜索词，否则沿用上次搜索词并全选输入框。
 */
class SearchBar(textArea: RSyntaxTextArea) : JToolBar() {
	private val rTextArea: RSyntaxTextArea = textArea

	private val searchField: JTextField
	private val resultCountLabel: JLabel
	private val markAllCB: JToggleButton
	private val regexCB: JToggleButton
	private val wholeWordCB: JToggleButton
	private val matchCaseCB: JToggleButton
	private var notFound = false

	init {
		val findLabel = JLabel(NLS.str("search.find") + ':')
		add(findLabel)

		searchField = JTextField(30)
		searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)
		searchField.addKeyListener(object : KeyAdapter() {
			override fun keyReleased(e: KeyEvent) {
				when (e.getKeyCode()) {
					KeyEvent.VK_ENTER -> {
						// 跳过
					}

					KeyEvent.VK_ESCAPE -> toggle()

					else -> search(0)
				}
			}
		})
		searchField.addActionListener { search(1) }
		TextStandardActions.attach(searchField)
		add(searchField)

		val forwardListener = ActionListener { search(1) }

		resultCountLabel = JLabel()
		resultCountLabel.setBorder(EmptyBorder(0, 10, 0, 10))
		resultCountLabel.setForeground(Color.GRAY)
		add(resultCountLabel)
		setResultCount(0)

		matchCaseCB = JToggleButton()
		matchCaseCB.setIcon(Icons.ICON_MATCH)
		matchCaseCB.setSelectedIcon(Icons.ICON_MATCH_SELECTED)
		matchCaseCB.setToolTipText(NLS.str("search.match_case"))
		matchCaseCB.addActionListener(forwardListener)
		add(matchCaseCB)

		wholeWordCB = JToggleButton()
		wholeWordCB.setIcon(Icons.ICON_WORDS)
		wholeWordCB.setSelectedIcon(Icons.ICON_WORDS_SELECTED)
		wholeWordCB.setToolTipText(NLS.str("search.whole_word"))
		wholeWordCB.addActionListener(forwardListener)
		add(wholeWordCB)

		regexCB = JToggleButton()
		regexCB.setIcon(Icons.ICON_REGEX)
		regexCB.setSelectedIcon(Icons.ICON_REGEX_SELECTED)
		regexCB.setToolTipText(NLS.str("search.regex"))
		regexCB.addActionListener(forwardListener)
		add(regexCB)

		val prevButton = JButton()
		prevButton.setIcon(Icons.ICON_UP)
		prevButton.setToolTipText(NLS.str("search.previous"))
		prevButton.addActionListener { search(-1) }
		prevButton.setBorderPainted(false)
		add(prevButton)

		val nextButton = JButton()
		nextButton.setIcon(Icons.ICON_DOWN)
		nextButton.setToolTipText(NLS.str("search.next"))
		nextButton.addActionListener { search(1) }
		nextButton.setBorderPainted(false)
		add(nextButton)

		markAllCB = JToggleButton()
		markAllCB.setIcon(Icons.ICON_MARK)
		markAllCB.setSelectedIcon(Icons.ICON_MARK_SELECTED)
		markAllCB.setToolTipText(NLS.str("search.mark_all"))
		markAllCB.addActionListener(forwardListener)
		add(markAllCB)

		val closeButton = JButton()
		closeButton.setIcon(Icons.ICON_CLOSE)
		closeButton.addActionListener { toggle() }
		closeButton.setBorderPainted(false)
		add(closeButton)

		setFloatable(false)
		setVisible(false)
	}

	/*
	 * 复刻 IntelliJ 查找栏行为：
	 * 1. 若用户选中了文本，则用它作为搜索词；否则沿用上次搜索词（没有则为空）
	 * 2. 全选搜索框内容并聚焦
	 */
	fun showAndFocus() {
		setVisible(true)

		val selectedText = rTextArea.getSelectedText()
		if (!StringUtils.isEmpty(selectedText)) {
			searchField.setText(selectedText)
		}

		searchField.selectAll()
		searchField.requestFocus()
	}

	fun toggle() {
		val visible = !isVisible()
		setVisible(visible)

		if (visible) {
			val preferText = rTextArea.getSelectedText()
			if (!StringUtils.isEmpty(preferText)) {
				searchField.setText(preferText)
			}
			searchField.selectAll()
			searchField.requestFocus()
		} else {
			rTextArea.requestFocus()
		}
	}

	private fun search(direction: Int) {
		val searchText = searchField.getText()
		if (searchText == null || searchText.isEmpty() || rTextArea.getText() == null) {
			setResultCount(0)
			return
		}

		val forward = direction >= 0
		val matchCase = matchCaseCB.isSelected()
		val regex = regexCB.isSelected()
		val wholeWord = wholeWordCB.isSelected()

		val context = SearchContext()
		context.setSearchFor(searchText)
		context.setMatchCase(matchCase)
		context.setRegularExpression(regex)
		context.setSearchForward(forward)
		context.setWholeWord(wholeWord)
		context.setSearchWrap(true)

		// 即使“全部标记”开关关闭也开启它：这是通过 SearchEngine 统计匹配数的唯一办法
		context.setMarkAll(true)

		// TODO hack: 把光标移到上一次搜索之前，避免直接跳到下一个匹配处
		if (direction == 0 && !notFound) {
			try {
				val caretPos = rTextArea.getCaretPosition()
				val lineNum = rTextArea.getLineOfOffset(caretPos) - 1
				if (lineNum > 1) {
					rTextArea.setCaretPosition(rTextArea.getLineStartOffset(lineNum))
				}
			} catch (e: BadLocationException) {
				LOG.error("Caret move error", e)
			}
		}

		val result = SearchEngine.find(rTextArea, context)

		setResultCount(result.getMarkedCount())

		// 如果“全部标记”关闭，则清除高亮结果
		if (!markAllCB.isSelected()) {
			context.setMarkAll(false)
			SearchEngine.markAll(rTextArea, context)
		}

		notFound = !result.wasFound()
		if (notFound) {
			searchField.putClientProperty("JComponent.outline", "error")
		} else {
			searchField.putClientProperty("JComponent.outline", "")
		}
		searchField.repaint()
	}

	private fun setResultCount(count: Int) {
		val exceedsLimit = count > MAX_RESULT_COUNT
		val plusSign = if (exceedsLimit) "+" else ""
		val displayCount = if (exceedsLimit) MAX_RESULT_COUNT else count

		resultCountLabel.setText(NLS.str("search.results", plusSign, displayCount))
	}

	companion object {
		private const val serialVersionUID = 1836871286618633003L

		private val LOG = LoggerFactory.getLogger(SearchBar::class.java)
		private const val MAX_RESULT_COUNT = 999
	}
}
