package kadx.gui.events.types

import kadx.api.plugins.events.IKadxEvent
import kadx.api.plugins.events.IKadxEvents
import kadx.api.plugins.events.KadxEventType
import kadx.core.plugins.events.KadxEventsImpl
import java.util.function.Consumer

/**
 * GUI 事件总线实现：同时管理“全局事件”与“项目事件”两套总线。
 *
 * **做什么**：
 * - [global]：应用级事件，监听器在应用生命周期内一直有效；
 * - `project`：项目级事件，仅在项目打开期间保留监听器，关闭项目时通过 [reset] 清空。
 *
 * [send] 会向两套总线同时广播；[addListener]/[removeListener]/[reset] 只作用于项目总线。
 *
 * **为什么保留 `Consumer` 参数类型**：核心接口 [IKadxEvents] 显式声明
 * `java.util.function.Consumer`，覆写签名必须精确匹配，故不改为 Kotlin 函数类型。
 */
class KadxGuiEventsImpl : IKadxEvents {

	private val global: IKadxEvents = KadxEventsImpl()
	private val project: IKadxEvents = KadxEventsImpl()

	/** 全局事件总线（应用级监听器注册于此）。 */
	fun global(): IKadxEvents = global

	override fun send(event: IKadxEvent) {
		global.send(event)
		project.send(event)
	}

	override fun <E : IKadxEvent> addListener(eventType: KadxEventType<E>, listener: Consumer<E>) {
		project.addListener(eventType, listener)
	}

	override fun <E : IKadxEvent> removeListener(eventType: KadxEventType<E>, listener: Consumer<E>) {
		project.removeListener(eventType, listener)
	}

	override fun reset() {
		project.reset()
	}
}
