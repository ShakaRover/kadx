package jadx.gui.ui.tab

import jadx.gui.ui.codearea.EditorViewState
import jadx.gui.utils.JumpPosition
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 通过实现 [ITabStatesListener] 把 [TabsController] 的事件写入日志的工具类。
 */
class LogTabStates : ITabStatesListener {

	override fun onTabBookmarkChange(blueprint: TabBlueprint) {
		LOG.debug("onTabBookmarkChange: blueprint={}", blueprint)
	}

	override fun onTabClose(blueprint: TabBlueprint) {
		LOG.debug("onTabClose: blueprint={}", blueprint)
	}

	override fun onTabCodeJump(blueprint: TabBlueprint, prevPos: JumpPosition?, newPos: JumpPosition) {
		LOG.debug("onTabCodeJump: blueprint={}, prevPos={}, newPos={}", blueprint, prevPos, newPos)
	}

	override fun onTabOpen(blueprint: TabBlueprint) {
		LOG.debug("onTabOpen: blueprint={}", blueprint)
	}

	override fun onTabPinChange(blueprint: TabBlueprint) {
		LOG.debug("onTabPinChange: blueprint={}", blueprint)
	}

	override fun onTabPositionFirst(blueprint: TabBlueprint) {
		LOG.debug("onTabPositionFirst: blueprint={}", blueprint)
	}

	override fun onTabRestore(blueprint: TabBlueprint, viewState: EditorViewState) {
		LOG.debug("onTabRestore: blueprint={}, viewState={}", blueprint, viewState)
	}

	override fun onTabSave(blueprint: TabBlueprint, viewState: EditorViewState) {
		LOG.debug("onTabSave: blueprint={}, viewState={}", blueprint, viewState)
	}

	override fun onTabSelect(blueprint: TabBlueprint) {
		LOG.debug("onTabSelect: blueprint={}", blueprint)
	}

	override fun onTabSmaliJump(blueprint: TabBlueprint, pos: Int, debugMode: Boolean) {
		LOG.debug("onTabSmaliJump: blueprint={}, pos={}, debugMode={}", blueprint, pos, debugMode)
	}

	override fun onTabsReorder(blueprints: MutableList<TabBlueprint>) {
		LOG.debug("onTabsReorder: blueprints={}", blueprints)
	}

	override fun onTabsRestoreDone() {
		LOG.debug("onTabsRestoreDone")
	}

	override fun onTabVisibilityChange(blueprint: TabBlueprint) {
		LOG.debug("onTabVisibilityChange: blueprint={}", blueprint)
	}

	override fun onTabPreviewChange(blueprint: TabBlueprint) {
		LOG.debug("onTabPreviewChange: blueprint={}", blueprint)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(LogTabStates::class.java)
	}
}
