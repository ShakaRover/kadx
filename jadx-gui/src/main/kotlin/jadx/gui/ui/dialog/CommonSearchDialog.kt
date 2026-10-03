package jadx.gui.ui.dialog

import jadx.api.JavaNode
import jadx.gui.logs.LogOptions
import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JResSearchNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.panel.ProgressPanel
import jadx.gui.ui.tab.TabsController
import jadx.gui.utils.CacheObject
import jadx.gui.utils.JNodeCache
import jadx.gui.utils.JumpPosition
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.NodeLabel
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.SearchContext
import org.fife.ui.rtextarea.SearchEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Collections
import java.util.Enumeration
import java.util.HashSet
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn

/**
 * 搜索类对话框的公共基类（搜索结果表格 + 进度条 + 按钮栏）。
 *
 * **做什么**：统一维护结果表模型/视图、进度面板、打开与复制结果等逻辑，
 * 由 [SearchDialog]、[UsageDialog]、[UsageDialogPlus] 继承。
 *
 * **为什么保留 Swing 线程模型**：所有 UI 更新都在 EDT 上执行，不引入协程。
 *
 * **为什么不是 `data class`**：它是有状态的 Swing 窗口。
 */
abstract class CommonSearchDialog(
	protected val mainWindow: MainWindow,
	protected val windowTitle: String,
) : JFrame() {

	protected val tabsController: TabsController = mainWindow.getTabsController()
	protected val cache: CacheObject = mainWindow.getCacheObject()
	protected val codeFont: Font = mainWindow.getSettings().getCodeFont()

	protected lateinit var resultsModel: ResultsModel
	protected lateinit var resultsTable: ResultsTable
	protected lateinit var resultsInfoLabel: JLabel
	protected lateinit var progressInfoLabel: JLabel
	protected lateinit var warnLabel: JLabel
	protected lateinit var progressPane: ProgressPanel

	private var highlightContext: SearchContext? = null

	init {
		UiUtils.setWindowIcons(this)
		updateTitle("")
	}

	protected abstract fun openInit()

	protected abstract fun loadFinished()

	protected abstract fun loadStart()

	fun loadWindowPos() {
		if (!mainWindow.getSettings().loadWindowPos(this)) {
			setSize(800, 500)
		}
	}

	private fun updateTitle(searchText: String?) {
		if (searchText == null || searchText.isEmpty() || searchText.trim().isEmpty()) {
			title = windowTitle
		} else {
			title = "$windowTitle: $searchText"
		}
	}

	fun updateHighlightContext(text: String, caseSensitive: Boolean, regexp: Boolean, wholeWord: Boolean) {
		updateTitle(text)
		highlightContext = SearchContext(text).apply {
			setMatchCase(caseSensitive)
			setWholeWord(wholeWord)
			setRegularExpression(regexp)
			setMarkAll(true)
		}
	}

	fun disableHighlight() {
		highlightContext = null
	}

	protected fun registerInitOnOpen() {
		addWindowListener(object : WindowAdapter() {
			override fun windowOpened(e: WindowEvent) {
				SwingUtilities.invokeLater { openInit() }
			}
		})
	}

	protected open fun openSelectedItem() {
		val node = getSelectedNode() ?: return
		openItem(node)
	}

	protected open fun openItem(node: JNode) {
		if (node is JResSearchNode) {
			val jmpPos = JumpPosition(node.getResNode(), node.getPos())
			tabsController.codeJump(jmpPos)
		} else {
			tabsController.codeJump(node)
		}
		if (!mainWindow.getSettings().isKeepCommonDialogOpen) {
			dispose()
		}
	}

	private fun getSelectedNode(): JNode? {
		try {
			val selectedId = resultsTable.getSelectedRow()
			if (selectedId == -1 || selectedId >= resultsTable.getRowCount()) {
				return null
			}
			return resultsModel.getValueAt(selectedId, 0) as JNode
		} catch (e: Exception) {
			LOG.error("Failed to get results table selected object", e)
			return null
		}
	}

	override fun dispose() {
		mainWindow.getSettings().saveWindowPos(this)
		super.dispose()
	}

	protected fun initCommon() {
		UiUtils.addEscapeShortCutToDispose(this)
	}

	protected fun copyAllSearchResults() {
		val sb = StringBuilder()
		val uniqueRefs = HashSet<String>()
		for (node in resultsModel.rows) {
			val javaNode: JavaNode? = node.getJavaNode()
			if (javaNode != null) {
				val codeNodeRef = javaNode.getCodeNodeRef().toString()
				if (uniqueRefs.add(codeNodeRef)) {
					sb.append(codeNodeRef)
					if (node.hasDescString()) {
						val code = node.makeDescString()
						sb.append("\t\t").append(code)
					}
					sb.append("\n")
				}
			}
		}
		UiUtils.copyToClipboard(sb.toString())
	}

	protected open fun initButtonsPanel(): JPanel {
		progressPane = ProgressPanel(mainWindow, false)

		val cancelButton = JButton(NLS.str("search_dialog.cancel"))
		cancelButton.addActionListener { dispose() }
		val openBtn = JButton(NLS.str("search_dialog.open"))
		openBtn.addActionListener { openSelectedItem() }
		rootPane.defaultButton = openBtn
		val copyBtn = JButton(NLS.str("search_dialog.copy"))
		copyBtn.addActionListener { copyAllSearchResults() }

		val cbKeepOpen = JCheckBox(NLS.str("search_dialog.keep_open"))
		cbKeepOpen.isSelected = mainWindow.getSettings().isKeepCommonDialogOpen
		cbKeepOpen.addActionListener { mainWindow.getSettings().saveKeepCommonDialogOpen(cbKeepOpen.isSelected) }
		cbKeepOpen.setAlignmentY(Component.CENTER_ALIGNMENT)

		val buttonPane = JPanel()
		buttonPane.setLayout(BoxLayout(buttonPane, BoxLayout.LINE_AXIS))
		buttonPane.add(cbKeepOpen)
		buttonPane.add(Box.createRigidArea(Dimension(15, 0)))
		buttonPane.add(progressPane)
		buttonPane.add(Box.createRigidArea(Dimension(5, 0)))
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(copyBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(openBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelButton)
		return buttonPane
	}

	protected fun initResultsTable(): JPanel {
		val renderer = ResultsTableCellRenderer()
		resultsModel = ResultsModel()
		resultsModel.addTableModelListener { updateProgressLabel(false) }

		resultsTable = ResultsTable(resultsModel, renderer)
		resultsTable.setShowHorizontalLines(false)
		resultsTable.setDragEnabled(false)
		resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
		resultsTable.setColumnSelectionAllowed(false)
		resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
		resultsTable.setAutoscrolls(false)

		resultsTable.setDefaultRenderer(Any::class.java, renderer)
		val columns: Enumeration<TableColumn> = resultsTable.getColumnModel().getColumns()
		while (columns.hasMoreElements()) {
			val column = columns.nextElement()
			column.setCellRenderer(renderer)
		}

		resultsTable.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(evt: MouseEvent) {
				if (evt.getClickCount() == 2) {
					openSelectedItem()
				}
			}
		})
		resultsTable.addKeyListener(object : KeyAdapter() {
			override fun keyPressed(e: KeyEvent) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					openSelectedItem()
				}
			}
		})
		// 覆写复制动作：复制节点列的长字符串
		resultsTable.getActionMap().put(
			"copy",
			object : AbstractAction() {
				override fun actionPerformed(e: ActionEvent) {
					val selectedNode = getSelectedNode()
					if (selectedNode != null) {
						UiUtils.copyToClipboard(selectedNode.makeLongString())
					}
				}
			},
		)

		warnLabel = JLabel()
		warnLabel.setForeground(Color.RED)
		warnLabel.setVisible(false)

		val scroll = JScrollPane(
			resultsTable,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
		)

		resultsInfoLabel = JLabel("")
		resultsInfoLabel.setFont(mainWindow.getSettings().getUiFont())

		progressInfoLabel = JLabel("")
		progressInfoLabel.setFont(mainWindow.getSettings().getUiFont())
		progressInfoLabel.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				mainWindow.showLogViewer(LogOptions.allWithLevel(ch.qos.logback.classic.Level.INFO))
			}
		})

		val resultsActionsPanel = JPanel()
		resultsActionsPanel.setLayout(BoxLayout(resultsActionsPanel, BoxLayout.LINE_AXIS))
		resultsActionsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0))
		addResultsActions(resultsActionsPanel)

		val resultsPanel = JPanel()
		resultsPanel.setLayout(BoxLayout(resultsPanel, BoxLayout.PAGE_AXIS))
		resultsPanel.add(warnLabel, BorderLayout.PAGE_START)
		resultsPanel.add(scroll, BorderLayout.CENTER)
		resultsPanel.add(resultsActionsPanel, BorderLayout.PAGE_END)
		return resultsPanel
	}

	protected open fun addResultsActions(resultsActionsPanel: JPanel) {
		resultsActionsPanel.add(Box.createRigidArea(Dimension(20, 0)))
		resultsActionsPanel.add(resultsInfoLabel)
		resultsActionsPanel.add(Box.createRigidArea(Dimension(20, 0)))
		resultsActionsPanel.add(progressInfoLabel)
		resultsActionsPanel.add(Box.createHorizontalGlue())
	}
	protected fun updateProgressLabel(complete: Boolean) {
		val count = resultsModel.getRowCount()
		val statusText: String
		if (complete) {
			statusText = NLS.str("search_dialog.results_complete", count)
		} else {
			statusText = NLS.str("search_dialog.results_incomplete", count)
		}
		resultsInfoLabel.setText(statusText)
	}

	protected fun showSearchState() {
		resultsInfoLabel.setText(NLS.str("search_dialog.tip_searching") + "...")
	}

	fun progressStartCommon() {
		progressPane.setIndeterminate(true)
		progressPane.setVisible(true)
		warnLabel.setVisible(false)
	}

	fun progressFinishedCommon() {
		progressPane.setVisible(false)
	}

	protected fun getNodeCache(): JNodeCache = mainWindow.getCacheObject().getNodeCache()

	protected class ResultsTable(
		resultsModel: ResultsModel,
		renderer: ResultsTableCellRenderer,
	) : JTable(resultsModel) {
		private val model: ResultsModel = resultsModel

		init {
			setRowHeight(renderer.getMaxRowHeight())
		}

		fun initColumnWidth() {
			val columnCount = getColumnCount()
			val width = getParent().getWidth()
			val colWidth = if (model.isAddDescColumn()) width / 2 else width
			columnModel.getColumn(0).setPreferredWidth(colWidth)
			for (col in 1 until columnCount) {
				columnModel.getColumn(col).setPreferredWidth(width)
			}
		}

		fun updateTable() {
			UiUtils.uiThreadGuard()
			val rowCount = getRowCount()
			if (rowCount == 0) {
				updateUI()
				return
			}
			val start = System.currentTimeMillis()
			val width = getParent().getWidth()
			val firstColumn = columnModel.getColumn(0)
			if (model.isAddDescColumn()) {
				if (firstColumn.getWidth().toDouble() > width * 0.8) {
					// 第一列过大时会遮住第二列，缩小它
					firstColumn.setPreferredWidth(width / 2)
				}
				val secondColumn = columnModel.getColumn(1)
				val columnMaxWidth = width * 2 // 设得足够大以跳过逐行检查
				if (secondColumn.getWidth() < columnMaxWidth) {
					secondColumn.setPreferredWidth(columnMaxWidth)
				}
			} else {
				firstColumn.setPreferredWidth(width)
			}
			updateUI()
			if (LOG.isDebugEnabled) {
				LOG.debug("Update results table in {}ms, count: {}", System.currentTimeMillis() - start, rowCount)
			}
		}

		override fun getValueAt(row: Int, column: Int): Any = model.getValueAt(row, column)

		override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int {
			// 结果表只有两列且很宽，默认滚动增量过快，这里横向手动减速
			if (orientation == SwingConstants.HORIZONTAL) {
				return 30
			}
			return super.getScrollableUnitIncrement(visibleRect, orientation, direction)
		}
	}

	protected class ResultsModel : AbstractTableModel() {
		val rows: MutableList<JNode> = ArrayList()
		private var addDescColumn = false

		fun addAll(nodes: Collection<JNode>) {
			rows.addAll(nodes)
			if (!addDescColumn) {
				for (row in rows) {
					if (row.hasDescString()) {
						addDescColumn = true
						break
					}
				}
			}
		}

		fun clear() {
			addDescColumn = false
			rows.clear()
		}

		fun sort() {
			Collections.sort(rows)
		}

		fun isAddDescColumn(): Boolean = addDescColumn

		override fun getRowCount(): Int = rows.size

		override fun getColumnCount(): Int = 2

		override fun getColumnName(index: Int): String = COLUMN_NAMES[index]

		override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = rows[rowIndex]

		companion object {
			private val COLUMN_NAMES = arrayOf(NLS.str("search_dialog.col_node"), NLS.str("search_dialog.col_code"))
		}
	}

	protected inner class ResultsTableCellRenderer : TableCellRenderer {
		private val label: NodeLabel
		private val codeArea: RSyntaxTextArea
		private val emptyLabel: NodeLabel
		private val codeSelectedColor: Color
		private val codeBackground: Color

		init {
			codeArea = AbstractCodeArea.getDefaultArea(mainWindow)
			codeArea.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10))
			codeArea.setRows(1)
			codeBackground = codeArea.getBackground()
			codeSelectedColor = codeArea.getSelectionColor()
			label = NodeLabel()
			label.setOpaque(true)
			label.setFont(codeArea.getFont())
			label.setHorizontalAlignment(SwingConstants.LEFT)
			emptyLabel = NodeLabel()
			emptyLabel.setOpaque(true)
		}

		override fun getTableCellRendererComponent(
			table: JTable?,
			obj: Any?,
			isSelected: Boolean,
			hasFocus: Boolean,
			row: Int,
			column: Int,
		): Component {
			if (obj == null || table == null) {
				return emptyLabel
			}
			val comp = makeCell(obj as JNode, column)
			updateSelection(table, comp, column, isSelected)
			return comp
		}

		private fun updateSelection(table: JTable, comp: Component, column: Int, isSelected: Boolean) {
			if (column == 1) {
				if (isSelected) {
					comp.setBackground(codeSelectedColor)
				} else {
					comp.setBackground(codeBackground)
				}
			} else {
				if (isSelected) {
					comp.setBackground(table.getSelectionBackground())
					comp.setForeground(table.getSelectionForeground())
				} else {
					comp.setBackground(table.getBackground())
					comp.setForeground(table.getForeground())
				}
			}
		}

		private fun makeCell(node: JNode, column: Int): Component {
			if (column == 0) {
				label.disableHtml(node.disableHtml())
				label.setText(node.makeLongStringHtml())
				label.setToolTipText(node.getTooltip())
				label.setIcon(node.getIcon())
				return label
			}
			if (!node.hasDescString()) {
				return emptyLabel
			}
			codeArea.setSyntaxEditingStyle(node.getSyntaxName())
			val descStr = checkNotNull(node.makeDescString())
			codeArea.setText(descStr)
			codeArea.setColumns(descStr.length + 1)
			val ctx = highlightContext
			if (ctx != null) {
				SearchEngine.markAll(codeArea, ctx)
			}
			return codeArea
		}

		fun getMaxRowHeight(): Int {
			label.setText("Text")
			codeArea.setText("Text")
			return maxOf(getCompHeight(label), getCompHeight(codeArea))
		}

		private fun getCompHeight(comp: Component): Int = maxOf(comp.getHeight(), comp.getPreferredSize().height)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CommonSearchDialog::class.java)
		private const val serialVersionUID = 8939332306115370276L
	}
}
