package kadx.gui.ui.tab

import kadx.api.JavaClass
import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.annotations.NodeDeclareRef
import kadx.gui.jobs.TaskWithExtraOnFinish
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JNode
import kadx.gui.ui.MainWindow
import kadx.gui.ui.codearea.EditorViewState
import kadx.gui.utils.JumpPosition
import kadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 标签页总控制器：所有标签页状态变更的唯一入口。
 *
 * **做什么**：
 * - 以 [JNode] 为键维护 [TabBlueprint]（持久状态）；
 * - 维护监听者列表，把状态变化广播给 [ITabStatesListener]；
 * - 处理代码跳转（可能需要在后台加载类，再回到 EDT 滚动到目标位置）。
 *
 * **线程模型（N1）**：后台加载使用 [kadx.gui.jobs] 的协程任务执行器，
 * UI 更新用 `UiUtils.uiRun`（内部即 `SwingUtilities.invokeLater`）。
 *
 * **为什么不是 `data class`**：有状态的可变控制器，需要按身份比较。
 */
class TabsController(private val mainWindow: MainWindow) {

	private val tabsMap: MutableMap<JNode, TabBlueprint> = HashMap()
	private val listeners: MutableList<ITabStatesListener> = ArrayList()

	private var forceClose = false
	private var selectedTab: TabBlueprint? = null

	init {
		if (UiUtils.KADX_GUI_DEBUG) {
			addListener(LogTabStates())
		}
	}

	fun getMainWindow(): MainWindow = mainWindow

	fun addListener(listener: ITabStatesListener) {
		listeners.add(listener)
	}

	fun removeListener(listener: ITabStatesListener) {
		listeners.remove(listener)
	}

	fun getTabByNode(node: JNode): TabBlueprint? = tabsMap[node]

	fun openTab(node: JNode): TabBlueprint = openTab(node, false, false)

	fun openTab(node: JNode, hidden: Boolean, preview: Boolean): TabBlueprint {
		if (!node.hasContent()) {
			LOG.warn("Can't open tab for node without content, node: {}", node)
		}
		val existing = getTabByNode(node)
		val blueprint: TabBlueprint
		if (existing == null) {
			val newBlueprint = TabBlueprint(node)
			newBlueprint.isHidden = hidden
			newBlueprint.isPreviewTab = preview
			tabsMap[node] = newBlueprint
			listeners.forEach { l -> l.onTabOpen(newBlueprint) }
			if (hidden) {
				listeners.forEach { l -> l.onTabVisibilityChange(newBlueprint) }
			}
			blueprint = newBlueprint
		} else {
			blueprint = existing
		}
		setTabHiddenInternal(blueprint, hidden)
		if (!blueprint.isCreated) {
			LOG.warn("No content panel for node: {}", node)
			closeTabForce(blueprint)
		}
		return blueprint
	}

	fun previewTab(node: JNode): TabBlueprint {
		val blueprint = getPreviewTab()
		if (blueprint != null) {
			closeTab(blueprint.node)
		}
		return openTab(node, false, true)
	}

	fun selectTab(node: JNode) {
		selectTab(node, false)
	}

	fun selectTab(node: JNode, fromTree: Boolean) {
		val current = selectedTab
		if (current != null && current.node === node) {
			// 已经选中
			return
		}
		val newTab = if (mainWindow.getSettings().isEnablePreviewTab && fromTree) {
			previewTab(node)
		} else {
			openTab(node)
		}
		selectedTab = newTab
		listeners.forEach { l -> l.onTabSelect(newTab) }
	}

	fun deselectTab() {
		selectedTab = null
	}

	/**
	 * 跳转到节点定义处。
	 */
	fun codeJump(node: JNode) {
		codeJump(node, false)
	}

	/**
	 * 跳转到节点定义处。
	 *
	 * 若目标是内部类，则需要先加载其顶层父类，再在父类代码中搜索声明位置。
	 */
	fun codeJump(node: JNode, fromTree: Boolean) {
		val parentCls = node.getJParent()
		if (parentCls != null) {
			// 处理跳转到内部类、方法或字段：先加载父类，再搜索位置并跳转
			val cls = parentCls.getCls()
			val origTopCls = cls.getOriginalTopParentClass()
			val codeParent = cls.getTopParentClass()
			if (codeParent != origTopCls) {
				val jumpCls = checkNotNull(mainWindow.getCacheObject().nodeCache.makeFrom(codeParent))
				loadCodeWithUIAction(jumpCls, Runnable { jumpToInnerClass(node, codeParent, jumpCls, fromTree) })
				return
			}
		}
		val clsRootClass = node.getRootClass()
		if (clsRootClass == null) {
			// 不是类，直接选中标签页（不滚动位置）
			selectTab(node, fromTree)
			return
		}
		loadCodeWithUIAction(clsRootClass, Runnable { codeJump(JumpPosition(node), fromTree) })
	}

	private fun loadCodeWithUIAction(cls: JClass, action: Runnable) {
		val loadTask = cls.getLoadTask()
		if (loadTask == null) {
			// 已经加载完成
			UiUtils.uiRun(action)
			return
		}
		mainWindow.getBackgroundExecutor().execute(TaskWithExtraOnFinish(loadTask, action))
	}

	/**
	 * 在 jumpCls 中搜索并跳转到原始节点。
	 */
	private fun jumpToInnerClass(node: JNode, codeParent: JavaClass, jumpCls: JClass, fromTree: Boolean) {
		codeParent.getCodeInfo().codeMetadata.searchDown<Boolean?>(0) { pos, ann ->
			if (ann.annType == ICodeAnnotation.AnnType.DECLARATION) {
				val declNode = (ann as NodeDeclareRef).getNode()
				if (declNode == node.getJavaNode()?.getCodeNodeRef()) {
					codeJump(JumpPosition(jumpCls, pos), fromTree)
					return@searchDown true
				}
			}
			null
		}
	}

	fun codeJump(pos: JumpPosition) {
		codeJump(pos, false)
	}

	/**
	 * 优先使用 [codeJump] 方法。
	 */
	fun codeJump(pos: JumpPosition, fromTree: Boolean) {
		val currentPosition = mainWindow.getTabbedPane().currentPosition
		val current = selectedTab
		if (current == null || current.node !== pos.getNode()) {
			selectTab(pos.getNode(), fromTree)
		}
		val activeTab = checkNotNull(selectedTab)
		listeners.forEach { l -> l.onTabCodeJump(activeTab, currentPosition, pos) }
	}

	fun smaliJump(cls: JClass, pos: Int, debugMode: Boolean) {
		selectTab(cls)
		val blueprint = checkNotNull(getTabByNode(cls))
		listeners.forEach { l -> l.onTabSmaliJump(blueprint, pos, debugMode) }
	}

	fun closeTab(node: JNode) {
		closeTab(node, false)
	}

	fun closeTab(node: JNode, considerPins: Boolean) {
		val blueprint = getTabByNode(node)
		if (blueprint != null) {
			closeTab(blueprint, considerPins)
		}
	}

	fun closeTab(blueprint: TabBlueprint, considerPins: Boolean) {
		if (forceClose) {
			closeTabForce(blueprint)
			return
		}
		if (!considerPins || !blueprint.isPinned) {
			if (!blueprint.isReferenced) {
				closeTabForce(blueprint)
			} else {
				closeTabSoft(blueprint)
			}
		}
	}

	/**
	 * 从各处彻底移除标签页。
	 */
	private fun closeTabForce(blueprint: TabBlueprint) {
		listeners.forEach { l -> l.onTabClose(blueprint) }
		tabsMap.remove(blueprint.node)
	}

	/**
	 * 从 TabbedPane 隐藏标签页。
	 */
	private fun closeTabSoft(blueprint: TabBlueprint) {
		setTabHidden(blueprint.node, true)
	}

	fun setTabPositionFirst(node: JNode) {
		val blueprint = openTab(node)
		listeners.forEach { l -> l.onTabPositionFirst(blueprint) }
	}

	fun setTabPinned(node: JNode, pinned: Boolean) {
		val blueprint = openTab(node)
		setTabPinnedInternal(blueprint, pinned)
	}

	fun setTabPinnedInternal(blueprint: TabBlueprint, pinned: Boolean) {
		if (blueprint.isPinned != pinned) {
			blueprint.isPreviewTab = false
			blueprint.isPinned = pinned
			listeners.forEach { l -> l.onTabPinChange(blueprint) }
		}
	}

	fun setTabBookmarked(node: JNode, bookmarked: Boolean) {
		val blueprint = openTab(node)
		setTabBookmarkedInternal(blueprint, bookmarked)
	}

	private fun setTabBookmarkedInternal(blueprint: TabBlueprint, bookmarked: Boolean) {
		if (blueprint.isBookmarked != bookmarked) {
			blueprint.isPreviewTab = false
			blueprint.isBookmarked = bookmarked
			listeners.forEach { l -> l.onTabBookmarkChange(blueprint) }
			removeTabIfNotReferenced(blueprint)
		}
	}

	fun setTabHidden(node: JNode, hidden: Boolean) {
		val blueprint = getTabByNode(node)
		setTabHiddenInternal(blueprint, hidden)
	}

	private fun setTabHiddenInternal(blueprint: TabBlueprint?, hidden: Boolean) {
		if (blueprint != null && blueprint.isHidden != hidden) {
			blueprint.isPreviewTab = false
			blueprint.isHidden = hidden
			listeners.forEach { l -> l.onTabVisibilityChange(blueprint) }
		}
	}

	fun setTabPreview(node: JNode, isPreview: Boolean) {
		val blueprint = getTabByNode(node)
		setTabPreviewInternal(blueprint, isPreview)
	}

	private fun setTabPreviewInternal(blueprint: TabBlueprint?, isPreview: Boolean) {
		if (blueprint != null && blueprint.isPreviewTab != isPreview) {
			blueprint.isPreviewTab = isPreview
			listeners.forEach { l -> l.onTabPreviewChange(blueprint) }
		}
	}

	private fun removeTabIfNotReferenced(blueprint: TabBlueprint) {
		if (blueprint.isHidden && !blueprint.isReferenced) {
			tabsMap.remove(blueprint.node)
		}
	}

	fun closeAllTabs() {
		closeAllTabs(false)
	}

	fun forceCloseAllTabs() {
		forceClose = true
		closeAllTabs()
		forceClose = false
		selectedTab = null
	}

	val isForceClose: Boolean get() = forceClose

	fun closeAllTabs(considerPins: Boolean) {
		tabsMap.values.toList().forEach { t -> closeTab(t.node, considerPins) }
	}

	fun unpinAllTabs() {
		tabsMap.values.forEach { t -> setTabPinned(t.node, false) }
	}

	fun unbookmarkAllTabs() {
		tabsMap.values.forEach { t -> setTabBookmarked(t.node, false) }
	}

	fun getSelectedTab(): TabBlueprint? = selectedTab

	val tabs: List<TabBlueprint> get() = tabsMap.values.toList()

	val openTabs: List<TabBlueprint> get() = tabsMap.values.toList()

	val pinnedTabs: List<TabBlueprint> get() = tabsMap.values.filter { it.isPinned }

	val bookmarkedTabs: List<TabBlueprint> get() = tabsMap.values.filter { it.isBookmarked }

	fun getPreviewTab(): TabBlueprint? = tabsMap.values.firstOrNull { it.isPreviewTab }

	fun restoreEditorViewState(viewState: EditorViewState) {
		val node = viewState.getNode()
		val blueprint = openTab(node, viewState.isHidden, viewState.isPreviewTab)
		setTabPinnedInternal(blueprint, viewState.isPinned)
		setTabBookmarkedInternal(blueprint, viewState.isBookmarked)
		listeners.forEach { l -> l.onTabRestore(blueprint, viewState) }
		if (viewState.isActive) {
			selectTab(node)
		}
	}

	fun notifyRestoreEditorViewStateDone() {
		if (selectedTab == null && tabsMap.isNotEmpty()) {
			val node = tabsMap.values.iterator().next().node
			// TODO: 查明这个问题的原因
			LOG.warn("No active tab found, select {}", node)
			selectTab(node)
		}
		listeners.forEach { it.onTabsRestoreDone() }
	}

	val editorViewStates: List<EditorViewState> get() {
		val reorderedTabs = ArrayList(tabsMap.values)
		listeners.forEach { l -> l.onTabsReorder(reorderedTabs) }
		val states = ArrayList<EditorViewState>()
		for (blueprint in reorderedTabs) {
			states.add(getEditorViewState(blueprint))
		}
		return states
	}

	fun getEditorViewState(blueprint: TabBlueprint): EditorViewState {
		val viewState = EditorViewState(blueprint.node)
		listeners.forEach { l -> l.onTabSave(blueprint, viewState) }
		viewState.setActive(blueprint === selectedTab)
		viewState.setPinned(blueprint.isPinned)
		viewState.setBookmarked(blueprint.isBookmarked)
		viewState.setHidden(blueprint.isHidden)
		viewState.setPreviewTab(blueprint.isPreviewTab)
		return viewState
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TabsController::class.java)
	}
}
