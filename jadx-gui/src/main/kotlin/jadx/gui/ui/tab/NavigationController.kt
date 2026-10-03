package jadx.gui.ui.tab

import jadx.gui.ui.MainWindow
import jadx.gui.utils.JumpManager
import jadx.gui.utils.JumpPosition

/**
 * 前进/后退导航控制器。
 *
 * **做什么**：监听标签页的代码跳转事件，把跳转位置记入 [JumpManager]，
 * 并提供 [navBack] / [navForward] 两个入口。
 *
 * TODO: 后续可把跳转历史保存到项目文件，以便重新打开后恢复。
 */
class NavigationController(private val mainWindow: MainWindow) : ITabStatesListener {

	private val jumps = JumpManager()

	init {
		mainWindow.getTabsController().addListener(this)
	}

	fun navBack() {
		jump(jumps.getPrev())
	}

	fun navForward() {
		jump(jumps.getNext())
	}

	private fun jump(pos: JumpPosition?) {
		if (pos != null) {
			mainWindow.getTabsController().codeJump(pos)
		}
	}

	override fun onTabCodeJump(blueprint: TabBlueprint, prevPos: JumpPosition?, newPos: JumpPosition) {
		if (newPos == jumps.getCurrent()) {
			// 忽略由自身发起的跳转
			return
		}
		jumps.addPosition(prevPos)
		jumps.addPosition(newPos)
	}

	override fun onTabSmaliJump(blueprint: TabBlueprint, pos: Int, debugMode: Boolean) {
		// TODO: 记录 smali 跳转
	}

	fun reset() {
		jumps.reset()
	}

	fun dispose() {
		reset()
		mainWindow.getTabsController().removeListener(this)
	}
}
