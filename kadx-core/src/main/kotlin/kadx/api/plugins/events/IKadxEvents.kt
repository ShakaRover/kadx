package kadx.api.plugins.events

import java.util.function.Consumer

/**
 * 事件总线接口：负责发送事件、注册/移除监听器。
 *
 * **做什么**：插件或 GUI 通过 [send] 广播事件；通过 [addListener] 订阅某类事件，
 * 用 [removeListener] 取消订阅，用 [reset] 清空全部监听器。
 *
 * **为什么保持 Java 可实现**：`KadxEventsImpl`（kadx-core）与 `KadxGuiEventsImpl`（kadx-gui）
 * 都是 Java 实现类；泛型方法签名与 `Consumer` 参数保持与原 Java 完全一致。
 */
interface IKadxEvents {

	/**
	 * 发送一个事件对象。
	 * 公开事件类型见 [KadxEvents]。
	 */
	fun send(event: IKadxEvent)

	/**
	 * 为特定事件注册监听器。
	 * 公开事件类型见 [KadxEvents]。
	 */
	fun <E : IKadxEvent> addListener(eventType: KadxEventType<E>, listener: Consumer<E>)

	/**
	 * 移除特定事件的监听器。
	 * 监听器必须是同一个或 `equals` 相等的对象。
	 */
	fun <E : IKadxEvent> removeListener(eventType: KadxEventType<E>, listener: Consumer<E>)

	/**
	 * 清空所有监听器。
	 */
	fun reset()
}
