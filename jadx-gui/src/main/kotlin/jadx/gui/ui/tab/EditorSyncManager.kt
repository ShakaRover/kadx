package jadx.gui.ui.tab

import jadx.gui.ui.MainWindow
import jadx.gui.ui.panel.ContentPanel

/**
 * 编辑器与树的同步管理器。
 *
 * **做什么**：监听标签页选中/关闭事件，刷新十六进制查看菜单的可用状态，
 * 并在开启“总是选中已打开项”时把树选中项同步到当前标签页对应的节点。
 *
 * **线程模型**：所有回调都在 EDT 上执行，保持原 Swing 实现不变。
 */
class EditorSyncManager(
	private val mainWindow: MainWindow,
	private val tabbedPane: TabbedPane,
) : ITabStatesListener {

	init {
		mainWindow.getTabsController().addListener(this)
	}

	/** 把树的选中项同步到当前选中的内容面板对应节点。 */
	fun sync() {
		val selectedContentPanel = tabbedPane.selectedContentPanel
		if (selectedContentPanel != null) {
			mainWindow.selectNodeInTree(selectedContentPanel.getNode())
		}
	}

	override fun onTabSelect(blueprint: TabBlueprint) {
		mainWindow.updateHexViewMenuEnabled()
		if (mainWindow.getSettings().isAlwaysSelectOpened) {
			// 确认该蓝图确实打开了标签页（有些节点不会打开内容面板）
			val selectedContentPanel = tabbedPane.selectedContentPanel
			if (selectedContentPanel != null && selectedContentPanel.getNode() == blueprint.node) {
				sync()
			}
		}
	}

	override fun onTabClose(blueprint: TabBlueprint) {
		super<ITabStatesListener>.onTabClose(blueprint)
		mainWindow.updateHexViewMenuEnabled()
	}
}
