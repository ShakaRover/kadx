package jadx.gui.ui.codearea

import jadx.gui.treemodel.JNode
import jadx.gui.ui.panel.IViewStateSupport
import jadx.gui.ui.tab.TabbedPane
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point

/**
 * 基于文本代码的通用内容面板：内部是一个 [CodePanel]（搜索栏 + [CodeArea]）。
 *
 * **做什么**：用于展示普通 Java 代码或文本类资源（如 AndroidManifest.xml）。
 * 负责把加载、设置刷新、视图状态（光标/滚动位置）保存与恢复委托给 [CodePanel]。
 */
class CodeContentPanel(panel: TabbedPane, jnode: JNode) :
	AbstractCodeContentPanel(panel, jnode),
	IViewStateSupport {
	private val codePanel: CodePanel

	init {
		layout = BorderLayout()
		codePanel = CodePanel(CodeArea(this, jnode))
		add(codePanel, BorderLayout.CENTER)
		codePanel.load()
	}

	override fun loadSettings() {
		codePanel.loadSettings()
		updateUI()
	}

	val searchBar: SearchBar get() = codePanel.getSearchBar()

	override fun getCodeArea(): AbstractCodeArea = codePanel.getCodeArea()

	override fun getChildrenComponent(): Component = getCodeArea()

	override fun saveEditorViewState(viewState: EditorViewState) {
		val caretPos = codePanel.getCodeArea().getCaretPosition()
		val viewPoint = codePanel.getCodeScrollPane().getViewport().getViewPosition()
		viewState.setCaretPos(caretPos)
		viewState.setViewPoint(viewPoint)
	}

	override fun restoreEditorViewState(viewState: EditorViewState) {
		try {
			codePanel.getCodeScrollPane().getViewport().setViewPosition(viewState.getViewPoint())
			codePanel.getCodeArea().setCaretPosition(viewState.getCaretPos())
		} catch (e: Exception) {
			LOG.error("Failed to restore view state", e)
		}
	}

	override fun dispose() {
		codePanel.dispose()
	}

	companion object {
		private const val serialVersionUID = 5310536092010045565L
		private val LOG = LoggerFactory.getLogger(CodeContentPanel::class.java)
	}
}
