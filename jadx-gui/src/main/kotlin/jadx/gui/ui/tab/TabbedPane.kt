package jadx.gui.ui.tab

import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.jobs.SilentTask
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.AbstractCodeContentPanel
import jadx.gui.ui.codearea.ClassCodeContentPanel
import jadx.gui.ui.codearea.EditorViewState
import jadx.gui.ui.codearea.SmaliArea
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.panel.FontPanel
import jadx.gui.ui.panel.HtmlPanel
import jadx.gui.ui.panel.IViewStateSupport
import jadx.gui.ui.panel.ImagePanel
import jadx.gui.ui.tab.dnd.TabDndController
import jadx.gui.utils.JumpPosition
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

/**
 * 主标签栏：真正承载 [ContentPanel] 的 Swing 组件。
 *
 * **做什么**：实现 [ITabStatesListener]，把 [TabsController] 的状态变化映射为
 * `JTabbedPane` 的增删/选中操作；同时处理滚轮切换、Ctrl+Tab 切换、Ctrl+W 关闭等交互。
 *
 * **线程模型（phase 5.1）**：完全沿用原 Swing 线程模型，UI 更新都在 EDT 上，
 * 后台加载通过 [SilentTask]（内部仍是 `SwingWorker`/任务执行器）完成，**不引入协程**。
 *
 * **为什么不是 `data class`**：这是有状态的 Swing 组件。
 */
class TabbedPane(
	@Transient private val mainWindow: MainWindow,
	@Transient private val controller: TabsController,
) : JTabbedPane(),
	ITabStatesListener {

	@Transient
	private val tabsMap: MutableMap<JNode, ContentPanel> = HashMap()

	@Transient
	private var curTab: ContentPanel? = null

	@Transient
	private var lastTab: ContentPanel? = null

	@Transient
	private var dnd: TabDndController? = null

	init {
		controller.addListener(this)
		setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)

		val clickAdapter = object : MouseAdapter() {
			override fun mousePressed(e: MouseEvent) {
				val tabIndex = indexAtLocation(e.getX(), e.getY())
				if (tabIndex == -1 || tabIndex > getTabCount()) {
					return
				}
				val tab = getTabComponentAt(tabIndex) as TabComponent
				tab.dispatchEvent(e)
			}
		}
		addMouseListener(clickAdapter)

		addMouseWheelListener { event ->
			val currentDnd = dnd
			if (currentDnd != null && currentDnd.isDragging()) {
				return@addMouseWheelListener
			}
			var direction = event.getWheelRotation()
			if (getTabCount() == 0 || direction == 0) {
				return@addMouseWheelListener
			}
			direction = if (direction < 0) -1 else 1 // 归一化方向
			var index = getSelectedIndex()
			val maxIndex = getTabCount() - 1
			index += direction
			index = Math.max(0, Math.min(maxIndex, index))
			try {
				setSelectedIndex(index)
			} catch (e: IndexOutOfBoundsException) {
				// 忽略错误
			}
		}
		interceptTabKey()
		interceptCloseKey()
		enableSwitchingTabs()
	}

	private fun interceptTabKey() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(object : KeyEventDispatcher {
			private val ctrlDown = KeyEvent.CTRL_DOWN_MASK
			private var ctrlInterval = 0L

			override fun dispatchKeyEvent(e: KeyEvent): Boolean {
				val cur = System.currentTimeMillis()
				if (!FocusManager.isActive()) {
					return false // 标签不在焦点时不处理
				}
				val code = e.getKeyCode()
				val consume = code == KeyEvent.VK_TAB // 无论如何都消费 Tab 键事件
				val isReleased = e.getID() == KeyEvent.KEY_RELEASED
				if (isReleased) {
					if (code == KeyEvent.VK_CONTROL) {
						ctrlInterval = cur
					} else if (code == KeyEvent.VK_TAB) {
						var doSwitch = false
						if ((e.getModifiersEx() and ctrlDown) != 0) {
							doSwitch = lastTab != null && getTabCount() > 1
						} else {
							// ctrl 与 tab 的释放间隔非常近，几乎是同一时间，但 ctrl 先释放
							ctrlInterval = cur - ctrlInterval
							if (ctrlInterval <= 90) {
								doSwitch = lastTab != null && getTabCount() > 1
							}
						}
						if (doSwitch) {
							selectTab(checkNotNull(lastTab))
						}
					}
				} else if (consume && (e.getModifiersEx() and ctrlDown) == 0) {
					// 在源码与 smali 之间切换
					val currentTab = curTab
					if (currentTab is ClassCodeContentPanel) {
						currentTab.switchPanel()
					}
				}
				return consume
			}
		})
	}

	private fun interceptCloseKey() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(object : KeyEventDispatcher {
			private val closeKey = KeyEvent.VK_W
			private var canClose = true

			override fun dispatchKeyEvent(e: KeyEvent): Boolean {
				if (!FocusManager.isActive()) {
					return false // 标签不在焦点时不处理
				}
				if (e.getKeyCode() != closeKey) {
					return false // 只拦截关闭键事件
				}
				if (e.getID() == KeyEvent.KEY_RELEASED) {
					canClose = true // 关闭键松开后重新允许使用
					return false
				}
				if (e.isControlDown() && canClose) {
					// 关闭当前标签
					curTab?.let { closeCodePanel(it) }
					canClose = false // 关闭键松开前不再关闭更多标签
					return true
				}
				return false
			}
		})
	}

	private fun enableSwitchingTabs() {
		addChangeListener {
			val tab = getSelectedContentPanel()
			if (tab == null) { // 全部关闭
				curTab = null
				lastTab = null
				return@addChangeListener
			}
			FocusManager.focusOnCodePanel(tab)
			if (tab === curTab) { // 关闭的不是当前标签
				val last = lastTab
				if (last != null && indexOfComponent(last) == -1) { // lastTab 被关闭
					setLastTabAdjacentToCurTab()
				}
				return@addChangeListener
			}
			if (tab === lastTab) {
				if (indexOfComponent(curTab) == -1) { // curTab 被关闭且 lastTab 成为当前标签
					curTab = lastTab
					setLastTabAdjacentToCurTab()
					return@addChangeListener
				}
				// 正在 lastTab 与 curTab 之间切换
			}
			lastTab = curTab
			curTab = tab
		}
	}

	private fun setLastTabAdjacentToCurTab() {
		if (getTabCount() < 2) {
			lastTab = null
			return
		}
		val idx = indexOfComponent(curTab)
		lastTab = if (idx == 0) {
			getComponentAt(idx + 1) as ContentPanel
		} else {
			getComponentAt(idx - 1) as ContentPanel
		}
	}

	fun getMainWindow(): MainWindow = mainWindow

	fun getTabsController(): TabsController = controller

	private fun showCode(jumpPos: JumpPosition): ContentPanel? {
		UiUtils.uiThreadGuard()
		val jumpNode = jumpPos.getNode()
		val contentPanel = getTabByNode(jumpNode) ?: return null
		selectTab(contentPanel)
		var pos = jumpPos.getPos()
		if (pos <= 0) {
			LOG.warn("Invalid jump: {}", jumpPos, JadxRuntimeException())
			pos = Math.max(0, jumpNode.getPos())
		}
		contentPanel.scrollToPos(pos)
		return contentPanel
	}

	fun selectTab(contentPanel: ContentPanel) {
		controller.selectTab(contentPanel.getNode())
	}

	private fun smaliJump(cls: JClass, pos: Int, debugMode: Boolean) {
		var panel = getTabByNode(cls)
		if (panel == null) {
			panel = showCode(JumpPosition(cls, 1))
				?: throw JadxRuntimeException("Failed to open panel for JClass: $cls")
		} else {
			selectTab(panel)
		}
		val codePane = panel as ClassCodeContentPanel
		codePane.showSmaliPane()
		val smaliArea = codePane.getSmaliCodeArea() as SmaliArea
		if (debugMode) {
			smaliArea.scrollToDebugPos(pos)
		}
		smaliArea.scrollToPos(pos)
		smaliArea.requestFocus()
	}

	fun getCurrentPosition(): JumpPosition? {
		val selectedCodePanel = getSelectedContentPanel()
		if (selectedCodePanel is AbstractCodeContentPanel) {
			val codeArea = selectedCodePanel.getCodeArea()
			if (codeArea != null) {
				return codeArea.getCurrentPosition()
			}
		}
		return null
	}

	private fun addContentPanel(contentPanel: ContentPanel) {
		tabsMap[contentPanel.getNode()] = contentPanel
		val tabCount = getTabCount()
		add(contentPanel, tabCount)
		setTabComponentAt(tabCount, makeTabComponent(contentPanel))
	}

	fun closeCodePanel(contentPanel: ContentPanel) {
		closeCodePanel(contentPanel, false)
	}

	fun closeCodePanel(contentPanel: ContentPanel, considerPins: Boolean) {
		controller.closeTab(contentPanel.getNode(), considerPins)
	}

	fun getTabs(): List<ContentPanel> {
		val list = ArrayList<ContentPanel>(getTabCount())
		for (i in 0 until getTabCount()) {
			list.add(getComponentAt(i) as ContentPanel)
		}
		return list
	}

	fun getTabByNode(node: JNode): ContentPanel? = tabsMap[node]

	fun getTabComponentByNode(node: JNode): TabComponent? {
		val contentPanel = getTabByNode(node) ?: return null
		val index = indexOfComponent(contentPanel)
		if (index == -1) {
			return null
		}
		val component = getTabComponentAt(index)
		if (component !is TabComponent) {
			return null
		}
		return component
	}

	fun tabWithTitleExists(tabTitle: String): Boolean {
		try {
			for (i in 0 until getTabCount()) {
				val component = getTabComponentAt(i)
				if (component is TabComponent) {
					if (component.getTabTitle() == tabTitle) {
						return true
					}
				}
			}
		} catch (e: Exception) {
			LOG.warn("Failed to check tabs titles", e)
		}
		return false
	}

	fun refresh(node: JNode) {
		val panel = getTabByNode(node)
		if (panel != null) {
			setTabComponentAt(indexOfComponent(panel), makeTabComponent(panel))
			fireStateChanged()
		}
	}

	fun reloadInactiveTabs() {
		UiUtils.uiThreadGuard()
		val tabCount = getTabCount()
		if (tabCount == 1) {
			return
		}
		val current = getSelectedIndex()
		for (i in 0 until tabCount) {
			if (i == current) {
				continue
			}
			val oldPanel = getComponentAt(i) as ContentPanel
			val tab = controller.getTabByNode(oldPanel.getNode()) ?: continue
			val viewState = controller.getEditorViewState(tab)
			val node = oldPanel.getNode()
			val panel = checkNotNull(node.getContentPanel(this))
			FocusManager.listen(panel)
			tabsMap[node] = panel
			setComponentAt(i, panel)
			setTabComponentAt(i, makeTabComponent(panel))
			controller.restoreEditorViewState(viewState)
		}
		fireStateChanged()
	}

	fun getSelectedContentPanel(): ContentPanel? = getSelectedComponent() as ContentPanel?

	private fun makeTabComponent(contentPanel: ContentPanel): Component = TabComponent(this, contentPanel)

	fun closeAllTabs() {
		closeAllTabs(false)
	}

	fun closeAllTabs(considerPins: Boolean) {
		for (panel in getTabs()) {
			closeCodePanel(panel, considerPins)
		}
	}

	fun loadSettings() {
		for (i in 0 until getTabCount()) {
			(getComponentAt(i) as ContentPanel).loadSettings()
			(getTabComponentAt(i) as TabComponent).loadSettings()
		}
	}

	fun reset() {
		closeAllTabs()
		tabsMap.clear()
		curTab = null
		lastTab = null
		FocusManager.reset()
	}

	fun getFocusedComp(): Component? = FocusManager.getFocusedComp()

	fun getDnd(): TabDndController? = dnd

	fun setDnd(dnd: TabDndController) {
		this.dnd = dnd
	}

	override fun onTabOpen(blueprint: TabBlueprint) {
		if (blueprint.isHidden) {
			return
		}
		val node = blueprint.node
		val newPanel = node.getContentPanel(this)
		if (newPanel != null) {
			if (node !== newPanel.getNode()) {
				throw JadxRuntimeException("Incorrect node found in content panel")
			}
			FocusManager.listen(newPanel)
			addContentPanel(newPanel)
			blueprint.isCreated = true
		}
	}

	override fun onTabSelect(blueprint: TabBlueprint) {
		val contentPanel = getTabByNode(blueprint.node)
		if (contentPanel != null) {
			setSelectedComponent(contentPanel)
		}
	}

	override fun onTabCodeJump(blueprint: TabBlueprint, prevPos: JumpPosition?, newPos: JumpPosition) {
		// 排队任务，等待加载任务完成
		mainWindow.getBackgroundExecutor().execute(
			SilentTask {
				UiUtils.uiRun { showCode(newPos) }
			},
		)
	}

	override fun onTabSmaliJump(blueprint: TabBlueprint, pos: Int, debugMode: Boolean) {
		val node = blueprint.node
		if (node is JClass) {
			smaliJump(node, pos, debugMode)
		}
	}

	override fun onTabClose(blueprint: TabBlueprint) {
		val contentPanelToClose = getTabByNode(blueprint.node) ?: return

		val currentContentPanel = getSelectedContentPanel()
		if (currentContentPanel === contentPanelToClose) {
			val last = lastTab
			if (last != null && last.getNode() != null) {
				selectTab(last)
			} else if (getTabCount() > 1) {
				val removalIdx = indexOfComponent(contentPanelToClose)
				if (removalIdx > 0) { // 选中左侧标签
					setSelectedIndex(removalIdx - 1)
				} else if (removalIdx == 0) { // 选中右侧标签
					setSelectedIndex(removalIdx + 1)
				}
			} else {
				// 没有其它标签 => 通知控制器重置选中
				controller.deselectTab()
			}
		}

		tabsMap.remove(contentPanelToClose.getNode())
		remove(contentPanelToClose)
		contentPanelToClose.dispose()
	}

	override fun onTabPositionFirst(blueprint: TabBlueprint) {
		val contentPanel = getTabByNode(blueprint.node) ?: return
		setTabPosition(contentPanel, 0)
	}

	override fun onTabPinChange(blueprint: TabBlueprint) {
		val tabComponent = getTabComponentByNode(blueprint.node) ?: return
		tabComponent.update()
	}

	override fun onTabBookmarkChange(blueprint: TabBlueprint) {
		val tabComponent = getTabComponentByNode(blueprint.node) ?: return
		tabComponent.update()
	}

	override fun onTabVisibilityChange(blueprint: TabBlueprint) {
		if (!blueprint.isHidden && !tabsMap.containsKey(blueprint.node)) {
			onTabOpen(blueprint)
		}
		if (blueprint.isHidden && tabsMap.containsKey(blueprint.node)) {
			onTabClose(blueprint)
		}
	}

	override fun onTabPreviewChange(blueprint: TabBlueprint) {
		val tabComponent = getTabComponentByNode(blueprint.node) ?: return
		tabComponent.update()
	}

	override fun onTabRestore(blueprint: TabBlueprint, viewState: EditorViewState) {
		val contentPanel = getTabByNode(blueprint.node)
		if (contentPanel is IViewStateSupport) {
			contentPanel.restoreEditorViewState(viewState)
		}
	}

	override fun onTabsReorder(blueprints: MutableList<TabBlueprint>) {
		val newBlueprints = ArrayList<TabBlueprint>(blueprints.size)
		for (contentPanel in getTabs()) {
			val blueprint = controller.getTabByNode(contentPanel.getNode())
			if (blueprint != null) {
				newBlueprints.add(blueprint)
			}
		}
		// 补回隐藏标签
		val set = LinkedHashSet(blueprints)
		newBlueprints.forEach { set.remove(it) }
		newBlueprints.addAll(set)

		blueprints.clear()
		blueprints.addAll(newBlueprints)
	}

	override fun onTabSave(blueprint: TabBlueprint, viewState: EditorViewState) {
		val contentPanel = getTabByNode(blueprint.node)
		if (contentPanel is IViewStateSupport) {
			contentPanel.saveEditorViewState(viewState)
		}
	}

	private fun setTabPosition(contentPanel: ContentPanel, position: Int) {
		val tabComponent = getTabComponentByNode(contentPanel.getNode()) ?: return
		val restoreSelection = contentPanel === getSelectedContentPanel()
		remove(contentPanel)
		add(contentPanel, position)
		setTabComponentAt(position, tabComponent)
		if (restoreSelection) {
			setSelectedIndex(position)
		}
	}

	private object FocusManager : FocusListener {
		private var focusedComp: Component? = null

		fun isActive(): Boolean = focusedComp != null

		fun reset() {
			focusedComp = null
		}

		fun getFocusedComp(): Component? = focusedComp

		override fun focusGained(e: FocusEvent) {
			focusedComp = e.getSource() as Component
		}

		override fun focusLost(e: FocusEvent) {
			focusedComp = null
		}

		fun listen(pane: ContentPanel) {
			if (pane is ClassCodeContentPanel) {
				pane.getCodeArea().addFocusListener(this)
				pane.getSmaliCodeArea().addFocusListener(this)
				return
			}
			if (pane is AbstractCodeContentPanel) {
				pane.getChildrenComponent().addFocusListener(this)
				return
			}
			if (pane is HtmlPanel) {
				pane.getHtmlArea().addFocusListener(this)
				return
			}
			if (pane is ImagePanel) {
				pane.addFocusListener(this)
				return
			}
			if (pane is FontPanel) {
				pane.addFocusListener(this)
				return
			}
			// throw JadxRuntimeException("Add the new ContentPanel to TabbedPane.FocusManager: " + pane)
		}

		fun focusOnCodePanel(pane: ContentPanel) {
			if (pane is ClassCodeContentPanel) {
				SwingUtilities.invokeLater { pane.getCurrentCodeArea().requestFocus() }
				return
			}
			if (pane is AbstractCodeContentPanel) {
				SwingUtilities.invokeLater { pane.getChildrenComponent().requestFocus() }
				return
			}
			if (pane is HtmlPanel) {
				SwingUtilities.invokeLater { pane.getHtmlArea().requestFocusInWindow() }
				return
			}
			if (pane is ImagePanel) {
				SwingUtilities.invokeLater { pane.requestFocusInWindow() }
				return
			}
			if (pane is FontPanel) {
				SwingUtilities.invokeLater { pane.requestFocusInWindow() }
				return
			}
			// throw JadxRuntimeException("Add the new ContentPanel to TabbedPane.FocusManager: " + pane)
		}
	}

	companion object {
		private const val serialVersionUID = -8833600618794570904L

		private val LOG: Logger = LoggerFactory.getLogger(TabbedPane::class.java)
	}
}
