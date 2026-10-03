package jadx.gui.events.types

import jadx.api.plugins.events.IJadxEvent
import jadx.api.plugins.events.JadxEventType
import jadx.gui.events.JadxGuiEvents
import jadx.gui.treemodel.JRoot

/**
 * 类树刷新事件：携带需要刷新的类树根节点 [jRoot]。
 *
 * **做什么**：在类结构发生变化（如重命名、重新加载）后广播，监听者据此刷新树视图。
 *
 * **为什么保留显式 `getJRoot()`**：这是事件对象公共 API，Java/Kotlin 监听方都按
 * `event.getJRoot()` 访问，保持方法名不变。
 */
class TreeUpdate(private val jRoot: JRoot) : IJadxEvent {

	fun getJRoot(): JRoot = jRoot

	/** 协变返回具体事件类型；与核心接口的 `JadxEventType<out IJadxEvent>` 兼容。 */
	override fun getType(): JadxEventType<TreeUpdate> = JadxGuiEvents.TREE_UPDATE
}
