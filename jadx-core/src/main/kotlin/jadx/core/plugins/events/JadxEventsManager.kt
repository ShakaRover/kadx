package jadx.core.plugins.events

import jadx.api.plugins.events.IJadxEvent
import jadx.api.plugins.events.JadxEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.IdentityHashMap
import java.util.function.Consumer

/**
 * 事件发送与监听管理器。
 *
 * **做什么**：按事件类型维护监听器列表；[send] 时把事件投递到单并发度的协程作用域，
 * 由单个协程依次回调监听器，避免发送方阻塞，也避免并发回调带来的竞态。
 *
 * **协程化说明（N2c）**：原 `Executors.newSingleThreadExecutor` 改为
 * `CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))`，
 * 保持“串行回调”语义，同时不再占用专用线程。
 *
 * **为什么用 [IdentityHashMap]**：事件类型对象是「身份标识」，不应按 equals 合并，
 * 必须按引用比较，才能正确区分不同的 [JadxEventType] 实例。
 *
 * **N2d**：监听器列表的增删/遍历是极短的临界区且不挂起，保留监视器锁（[Synchronized]）。
 */
class JadxEventsManager {

	/** 事件类型 -> 监听器列表（按引用身份比较）。 */
	private val listeners: MutableMap<JadxEventType<*>, MutableList<Consumer<IJadxEvent>>> = IdentityHashMap()

	/** 事件回调作用域：单并发度，保证回调串行、有序。 */
	private val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

	/** 为某事件类型追加监听器。 */
	@Suppress("UNCHECKED_CAST")
	@Synchronized
	fun <E : IJadxEvent> addListener(eventType: JadxEventType<E>, listener: Consumer<E>) {
		listeners.computeIfAbsent(eventType) { ArrayList() }
			.add(listener as Consumer<IJadxEvent>)
	}

	/** 移除某事件类型的监听器；返回是否确实移除了。 */
	@Suppress("UNCHECKED_CAST")
	@Synchronized
	fun <E : IJadxEvent> removeListener(eventType: JadxEventType<E>, listener: Consumer<E>): Boolean {
		val eventListeners = listeners[eventType]
		if (eventListeners != null) {
			return eventListeners.remove(listener as Consumer<IJadxEvent>)
		}
		return false
	}

	/** 发送事件：把每个监听器回调投递到事件协程作用域。 */
	@Synchronized
	fun send(event: IJadxEvent) {
		val consumers = listeners[event.getType()]
		if (consumers != null) {
			for (consumer in consumers) {
				eventsScope.launch { consumer.accept(event) }
			}
		}
	}

	/** 清空所有监听器。 */
	@Synchronized
	fun reset() {
		listeners.clear()
	}

	/** 调试用：输出当前非空监听器类型的统计信息。 */
	fun listenersDebugStats(): String = listeners.entries
		.filter { it.value.isNotEmpty() }
		.joinToString(", ", "[", "]") { it.key.toString() + ":" + it.value.size }
}
