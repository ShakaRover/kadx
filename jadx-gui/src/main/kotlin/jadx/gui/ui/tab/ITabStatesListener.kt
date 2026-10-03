package jadx.gui.ui.tab

import jadx.gui.ui.codearea.EditorViewState
import jadx.gui.utils.JumpPosition

/**
 * 标签页（Tab）状态变化监听器。
 *
 * **做什么**：`TabsController` 在标签页被打开、选中、关闭、置顶、加书签等时，
 * 通过本接口通知所有已注册的监听者。默认实现均为空，监听者按需覆写。
 *
 * **为什么保持接口形态**：这是典型的观察者回调接口，方法名与 JVM 签名必须保持不变，
 * 以便 Java / Kotlin 两侧都能实现与调用。
 *
 * **可空性说明**：`prevPos` 可能为 `null`（未知上一个光标位置，或来自其它标签页）。
 */
interface ITabStatesListener {

	/**
	 * 标签页被加入 TabbedPane，但尚未成为活动（选中）标签。
	 */
	fun onTabOpen(blueprint: TabBlueprint) {
	}

	/**
	 * 标签页成为活动（选中）标签。
	 */
	fun onTabSelect(blueprint: TabBlueprint) {
	}

	/**
	 * 代码光标位置发生变化。
	 *
	 * @param prevPos 之前的光标位置；未知时可为 `null`，也可能来自另一个标签页
	 * @param newPos  新的光标位置，节点指向跳转目标节点
	 */
	fun onTabCodeJump(blueprint: TabBlueprint, prevPos: JumpPosition?, newPos: JumpPosition) {
	}

	fun onTabSmaliJump(blueprint: TabBlueprint, pos: Int, debugMode: Boolean) {
	}

	fun onTabClose(blueprint: TabBlueprint) {
	}

	fun onTabPositionFirst(blueprint: TabBlueprint) {
	}

	fun onTabPinChange(blueprint: TabBlueprint) {
	}

	fun onTabBookmarkChange(blueprint: TabBlueprint) {
	}

	fun onTabVisibilityChange(blueprint: TabBlueprint) {
	}

	fun onTabRestore(blueprint: TabBlueprint, viewState: EditorViewState) {
	}

	fun onTabsRestoreDone() {
	}

	/**
	 * 保存标签页顺序前触发，监听者可原地重排传入的可变列表。
	 */
	fun onTabsReorder(blueprints: MutableList<TabBlueprint>) {
	}

	fun onTabSave(blueprint: TabBlueprint, viewState: EditorViewState) {
	}

	fun onTabPreviewChange(blueprint: TabBlueprint) {
	}
}
