package jadx.gui.ui.codearea

import com.formdev.flatlaf.FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT
import jadx.api.DecompilationMode
import jadx.core.utils.Utils
import jadx.gui.treemodel.JClass
import jadx.gui.ui.codearea.mode.JCodeMode
import jadx.gui.ui.codearea.sync.CodeAreaSyncee
import jadx.gui.ui.codearea.sync.CodeAreaSyncer
import jadx.gui.ui.codearea.sync.CodeAreaSyncerAbstractFactory
import jadx.gui.ui.codearea.sync.fallback.FallbackSyncer
import jadx.gui.ui.panel.IViewStateSupport
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.ListenersHelper
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JCheckBox
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.CaretListener
import javax.swing.text.JTextComponent

/**
 * 展示单个类的面板，提供两种视图：
 *
 * - 选中类的 Java 源码（默认）
 * - 选中类的 Smali 源码
 *
 * **做什么**：底部标签页可切换 Java / Smali / Smali 字节码 / Simple / Fallback；
 * 勾选“Split view”后左右并排展示两套视图，并在光标移动时同步定位。
 */
class ClassCodeContentPanel(panel: TabbedPane, jClass: JClass) :
	AbstractCodeContentPanel(panel, jClass),
	IViewStateSupport {

	private val jCls: JClass = jClass
	private val caretListeners: ListenersHelper<JTextComponent, CaretListener> =
		ListenersHelper.buildForCaretListener()
	private val syncInProgress = AtomicBoolean(false)

	private val leftTabbedPane: JTabbedPane
	private var rightTabbedPane: JTabbedPane? = null
	private lateinit var javaCodePanel: CodePanel
	private lateinit var smaliCodePanel: CodePanel

	private var isSplitViewActivated = false

	init {
		leftTabbedPane = buildTabbedPane(jClass, true)
		addCustomControls(leftTabbedPane)
		initView()
		activateCodePanel(javaCodePanel)
	}

	private fun initView() {
		removeAll()
		layout = BorderLayout()
		border = EmptyBorder(0, 0, 0, 0)
		if (isSplitViewActivated) {
			val rightPane = buildTabbedPane(jCls, false)
			rightPane.setSelectedIndex(1) // 默认显示 Smali
			rightTabbedPane = rightPane

			val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTabbedPane, rightPane)
			splitPane.setResizeWeight(0.5)
			add(splitPane)
			revalidate()
			repaint()

			// 布局完成后设置分割线位置
			SwingUtilities.invokeLater { splitPane.setDividerLocation(0.5) }
		} else {
			disposeTabbedPane(rightTabbedPane)
			rightTabbedPane = null
			add(leftTabbedPane)
			revalidate()
			repaint()
		}
	}

	private fun buildTabbedPane(jCls: JClass, leftPanel: Boolean): JTabbedPane {
		val areaTabbedPane = JTabbedPane(JTabbedPane.BOTTOM)
		areaTabbedPane.setBorder(EmptyBorder(0, 0, 0, 0))
		areaTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)
		val javaPanel = CodePanel(CodeArea(this, jCls))
		val smaliPanel = CodePanel(SmaliArea(this, jCls, false))
		if (leftPanel) {
			this.javaCodePanel = javaPanel
			this.smaliCodePanel = smaliPanel
		}
		areaTabbedPane.add(javaPanel, NLS.str("tabs.code"))
		areaTabbedPane.add(smaliPanel, NLS.str("tabs.smali"))
		areaTabbedPane.add(CodePanel(SmaliArea(this, jCls, true)), NLS.str("tabs.smali_bytecode"))
		areaTabbedPane.add(CodePanel(CodeArea(this, JCodeMode(jCls, DecompilationMode.SIMPLE))), "Simple")
		areaTabbedPane.add(CodePanel(CodeArea(this, JCodeMode(jCls, DecompilationMode.FALLBACK))), "Fallback")
		areaTabbedPane.setMinimumSize(Dimension(200, 200))
		areaTabbedPane.addChangeListener { onCodePanelActivation(areaTabbedPane.getSelectedComponent() as CodePanel) }
		return areaTabbedPane
	}

	private fun onCodePanelActivation(selectedPanel: CodePanel) {
		selectedPanel.load()
		updateSync()
	}

	private fun activateCodePanel(javaCodePanel: CodePanel) {
		if (leftTabbedPane.getSelectedComponent() === javaCodePanel) {
			// 已经选中，change listener 不会触发，手动更新
			onCodePanelActivation(javaCodePanel)
		} else {
			leftTabbedPane.setSelectedComponent(javaCodePanel)
		}
	}

	private fun addCustomControls(tabbedPane: JTabbedPane) {
		val splitCheckBox = JCheckBox("Split view", false)
		splitCheckBox.addItemListener {
			val newSplitView = splitCheckBox.isSelected()
			if (isSplitViewActivated != newSplitView) {
				isSplitViewActivated = newSplitView
				initView()
			}
		}

		val trailing = JToolBar()
		trailing.setFloatable(false)
		trailing.setBorder(null)
		trailing.addSeparator(Dimension(50, 1))
		trailing.add(splitCheckBox)
		tabbedPane.putClientProperty(TABBED_PANE_TRAILING_COMPONENT, trailing)
	}

	private fun updateSync() {
		caretListeners.removeAll()
		if (!isSplitViewActivated) {
			return
		}
		val leftArea = getCodePanel(leftTabbedPane).getCodeArea()
		val rightArea = getCodePanel(rightTabbedPane).getCodeArea()
		if (leftArea is CodeAreaSyncee && rightArea is CodeAreaSyncee) {
			val leftSyncer = buildCodeAreaSyncer(leftArea)
			val rightSyncer = buildCodeAreaSyncer(rightArea)
			if (leftSyncer != null && rightSyncer != null) {
				caretListeners.add(leftArea, CaretListener { syncCodeArea(leftArea, rightArea, leftSyncer) })
				caretListeners.add(rightArea, CaretListener { syncCodeArea(rightArea, leftArea, rightSyncer) })
			}
		}
	}

	private fun syncCodeArea(fromArea: AbstractCodeArea, toArea: AbstractCodeArea, syncer: CodeAreaSyncer) {
		if (syncInProgress.get()) {
			return
		}
		try {
			syncInProgress.set(true)
			val synced = (toArea as CodeAreaSyncee).sync(syncer)
			if (!synced) {
				if (!FallbackSyncer.sync(fromArea, toArea)) {
					LOG.warn("Code pane area sync not possible")
				}
			}
		} catch (ex: Exception) {
			LOG.warn("Failed to sync method/class across views: {}", ex.getLocalizedMessage())
		} finally {
			syncInProgress.set(false)
		}
	}

	private fun getCodePanel(tabbedPane: JTabbedPane?): CodePanel {
		if (tabbedPane == null) {
			throw IllegalStateException("tabbedPane is null")
		}
		return tabbedPane.getSelectedComponent() as CodePanel
	}

	private fun buildCodeAreaSyncer(codeArea: AbstractCodeArea): CodeAreaSyncer? {
		if (codeArea is CodeAreaSyncerAbstractFactory) {
			return codeArea.createCodeAreaSyncer()
		}
		return null
	}

	override fun loadSettings() {
		for (component in leftTabbedPane.getComponents()) {
			if (component is CodePanel) {
				component.loadSettings()
			}
		}
		val right = rightTabbedPane
		if (right != null) {
			for (component in right.getComponents()) {
				if (component is CodePanel) {
					component.loadSettings()
				}
			}
		}
		updateUI()
	}

	override fun getCodeArea(): AbstractCodeArea = javaCodePanel.getCodeArea()

	override fun getChildrenComponent(): Component = getCodeArea()

	fun getJavaCodePanel(): CodePanel = javaCodePanel

	fun switchPanel() {
		val toSmali = leftTabbedPane.getSelectedComponent() === javaCodePanel
		activateCodePanel(if (toSmali) smaliCodePanel else javaCodePanel)
	}

	val currentCodeArea: AbstractCodeArea get() = (leftTabbedPane.getSelectedComponent() as CodePanel).getCodeArea()

	val smaliCodeArea: AbstractCodeArea get() = smaliCodePanel.getCodeArea()

	fun showSmaliPane() {
		activateCodePanel(smaliCodePanel)
	}

	override fun saveEditorViewState(viewState: EditorViewState) {
		val codePanel = leftTabbedPane.getSelectedComponent() as CodePanel
		val caretPos = codePanel.getCodeArea().getCaretPosition()
		val viewPoint: Point = codePanel.getCodeScrollPane().getViewport().getViewPosition()
		viewState.setSubPath(leftTabbedPane.getSelectedIndex().toString())
		viewState.setCaretPos(caretPos)
		viewState.setViewPoint(viewPoint)
	}

	override fun restoreEditorViewState(viewState: EditorViewState) {
		UiUtils.uiThreadGuard()
		val subPath = viewState.getSubPath()
		var activePanel: CodePanel? = null
		if (subPath == "java") {
			activePanel = javaCodePanel
		} else if (subPath == "smali") {
			activePanel = smaliCodePanel
		} else {
			try {
				val index = Utils.safeParseInt(subPath, 0)
				activePanel = leftTabbedPane.getComponentAt(index) as CodePanel
			} catch (e: Exception) {
				LOG.debug("Failed to restore active code panel: {}", subPath, e)
			}
		}
		val panel = activePanel ?: return
		activateCodePanel(panel)
		try {
			panel.getCodeScrollPane().getViewport().setViewPosition(viewState.getViewPoint())
		} catch (e: Exception) {
			LOG.debug("Failed to restore view position: {}", viewState.getViewPoint(), e)
		}
		val caretPos = viewState.getCaretPos()
		try {
			val codeArea = panel.getCodeArea()
			val codeLen = codeArea.getDocument().getLength()
			if (caretPos >= 0 && caretPos < codeLen) {
				codeArea.setCaretPosition(caretPos)
			}
		} catch (e: Exception) {
			LOG.debug("Failed to restore caret position: {}", caretPos, e)
		}
	}

	override fun dispose() {
		caretListeners.removeAll()
		disposeTabbedPane(leftTabbedPane)
		disposeTabbedPane(rightTabbedPane)
		super.dispose()
	}

	private fun disposeTabbedPane(tabbedPane: JTabbedPane?) {
		if (tabbedPane != null) {
			for (component in tabbedPane.getComponents()) {
				if (component is CodePanel) {
					component.dispose()
				}
			}
		}
	}

	companion object {
		private const val serialVersionUID = -7229931102504634591L

		private val LOG = LoggerFactory.getLogger(ClassCodeContentPanel::class.java)
	}
}
