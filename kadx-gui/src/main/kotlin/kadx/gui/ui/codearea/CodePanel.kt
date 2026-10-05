package kadx.gui.ui.codearea

import kadx.core.utils.StringUtils
import kadx.gui.settings.KadxSettings
import kadx.gui.settings.LineNumbersMode
import kadx.gui.ui.MainWindow
import kadx.gui.ui.dialog.SearchDialog
import kadx.gui.utils.CaretPositionFix
import kadx.gui.utils.DefaultPopupMenuListener
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.gui.utils.ui.MousePressedHandler
import org.fife.ui.rtextarea.LineNumberFormatter
import org.fife.ui.rtextarea.LineNumberList
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu.Separator
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.PopupMenuEvent

/**
 * 组合了搜索栏 [SearchBar] 与可滚动代码区 [CodeArea] 的面板。
 *
 * **做什么**：
 * - 在顶部放搜索栏，中间放代码区；
 * - 维护左侧行号列（普通行号 / 源码行号 / 关闭）；
 * - 注册 Ctrl+F 打开搜索栏，以及右键菜单里的“搜索/全局搜索”。
 */
class CodePanel(private val codeArea: AbstractCodeArea) : JPanel() {
	private val searchBar: SearchBar
	private val codeScrollPane: RTextScrollPane

	private var useSourceLines = false

	init {
		searchBar = SearchBar(codeArea)
		codeScrollPane = RTextScrollPane(codeArea)

		layout = BorderLayout()
		border = EmptyBorder(0, 0, 0, 0)
		add(searchBar, BorderLayout.NORTH)
		add(codeScrollPane, BorderLayout.CENTER)

		initLinesModeSwitch()
		initPopupMenuItems()
	}

	fun loadSettings() {
		codeArea.loadSettings()
		initLineNumbers()
	}

	fun load() {
		codeArea.load()
		initLineNumbers()
	}

	@Synchronized
	private fun initLineNumbers() {
		codeScrollPane.getGutter().setLineNumberFont(settings.codeFont)
		val mode = lineNumbersMode
		if (mode == LineNumbersMode.DISABLE) {
			codeScrollPane.setLineNumbersEnabled(false)
			return
		}
		useSourceLines = mode == LineNumbersMode.DEBUG
		applyLineFormatter()
		codeScrollPane.setLineNumbersEnabled(true)
	}

	@Synchronized
	private fun applyLineFormatter() {
		val linesFormatter = if (useSourceLines) {
			SourceLineFormatter(codeArea.getCodeInfo())
		} else {
			SIMPLE_LINE_FORMATTER
		}
		codeScrollPane.getGutter().setLineNumberFormatter(linesFormatter)
	}

	private val lineNumbersMode: LineNumbersMode get() {
		var mode = settings.lineNumbersMode
		val canShowDebugLines = canShowDebugLines()
		if (mode == LineNumbersMode.AUTO) {
			mode = if (canShowDebugLines) LineNumbersMode.DEBUG else LineNumbersMode.NORMAL
		} else if (mode == LineNumbersMode.DEBUG && !canShowDebugLines) {
			// 没有可显示的调试行，隐藏行号列
			mode = LineNumbersMode.DISABLE
		}
		return mode
	}

	private fun canShowDebugLines(): Boolean {
		if (codeArea is SmaliArea) {
			return false
		}
		val codeInfo = codeArea.getCodeInfo()
		if (!codeInfo.hasMetadata()) {
			return false
		}
		val lineMapping = codeInfo.codeMetadata.getLineMapping()
		if (lineMapping.isEmpty()) {
			return false
		}
		val uniqueDebugLines = HashSet(lineMapping.values)
		return uniqueDebugLines.size > 3
	}

	private fun initLinesModeSwitch() {
		val lineModeSwitch = MousePressedHandler {
			useSourceLines = !useSourceLines
			applyLineFormatter()
		}
		for (gutterComp in codeScrollPane.getGutter().getComponents()) {
			if (gutterComp is LineNumberList) {
				gutterComp.addMouseListener(lineModeSwitch)
			}
		}
	}

	private fun initPopupMenuItems() {
		val key = KeyStroke.getKeyStroke(KeyEvent.VK_F, UiUtils.ctrlButton())
		UiUtils.addKeyBinding(
			codeArea,
			key,
			"SearchAction",
			object : AbstractAction() {
				override fun actionPerformed(e: ActionEvent) {
					searchBar.showAndFocus()
				}
			},
		)
		val searchItem = JMenuItem()
		val globalSearchItem = JMenuItem()
		val searchAction: AbstractAction = object : AbstractAction(NLS.str("popup.search", "")) {
			override fun actionPerformed(e: ActionEvent) {
				searchBar.toggle()
			}
		}
		val globalSearchAction: AbstractAction = object : AbstractAction(NLS.str("popup.search_global", "")) {
			override fun actionPerformed(e: ActionEvent) {
				val mainWindow: MainWindow = codeArea.getContentPanel().mainWindow
				SearchDialog.searchText(mainWindow, codeArea.getSelectedText())
			}
		}
		searchItem.setAction(searchAction)
		globalSearchItem.setAction(globalSearchAction)
		val separator = Separator()
		val popupMenu = codeArea.getPopupMenu()
		popupMenu.addPopupMenuListener(object : DefaultPopupMenuListener {
			override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
				var preferText = codeArea.getSelectedText()
				if (!StringUtils.isEmpty(preferText)) {
					if (preferText.length >= 23) {
						preferText = preferText.substring(0, 20) + " ..."
					}
					searchAction.putValue(Action.NAME, NLS.str("popup.search", preferText))
					globalSearchAction.putValue(Action.NAME, NLS.str("popup.search_global", preferText))
					popupMenu.add(separator)
					popupMenu.add(globalSearchItem)
					popupMenu.add(searchItem)
				} else {
					popupMenu.remove(separator)
					popupMenu.remove(globalSearchItem)
					popupMenu.remove(searchItem)
				}
			}
		})
	}

	fun getSearchBar(): SearchBar = searchBar

	fun getCodeArea(): AbstractCodeArea = codeArea

	fun getCodeScrollPane(): JScrollPane = codeScrollPane

	fun refresh(caretFix: CaretPositionFix) {
		val viewport: JViewport = getCodeScrollPane().getViewport()
		val viewPosition: Point = viewport.getViewPosition()
		codeArea.refresh()
		initLineNumbers()

		SwingUtilities.invokeLater {
			viewport.setViewPosition(viewPosition)
			caretFix.restore()
		}
	}

	private val settings: KadxSettings get() = codeArea.getContentPanel().getTabbedPane().getMainWindow().getSettings()

	fun dispose() {
		codeArea.dispose()
	}

	companion object {
		private const val serialVersionUID = 1117721869391885865L

		private val SIMPLE_LINE_FORMATTER: LineNumberFormatter = object : LineNumberFormatter {
			override fun format(lineNumber: Int): String = lineNumber.toString()

			override fun getMaxLength(maxLineNumber: Int): Int = SourceLineFormatter.getNumberLength(maxLineNumber)
		}
	}
}
