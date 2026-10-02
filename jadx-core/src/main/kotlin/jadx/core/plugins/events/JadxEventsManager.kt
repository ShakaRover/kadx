package jadx.core.plugins.events

import jadx.api.plugins.events.IJadxEvent
import jadx.api.plugins.events.JadxEventType
import java.util.IdentityHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * 事件发送与监听管理器。
 *
 * **做什么**：按事件类型维护监听器列表；[send] 时把事件投递到单线程池，
 * 由该线程依次回调监听器，避免发送方阻塞，也避免并发回调带来的竞态。
 *
 * **为什么用 [IdentityHashMap]**：事件类型对象是「身份标识」，不应按 equals 合并，
 * 必须按引用比较，才能正确区分不同的 [JadxEventType] 实例。
 *
 * **K2 注意**：原 Java 的 `synchronized` 方法转成普通函数 + [Synchronized] 注解。
 */
class JadxEventsManager {

	/** 事件类型 -> 监听器列表（按引用身份比较）。 */
	private val listeners: MutableMap<JadxEventType<*>, MutableList<Consumer<IJadxEvent>>> = IdentityHashMap()

	/** 事件回调专用单线程池。 */
	private val eventsThreadPool: ExecutorService

	init {
		// TODO: allow to change threading strategy
		this.eventsThreadPool = Executors.newSingleThreadExecutor(makeThreadFactory())
	}

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

	/** 发送事件：把每个监听器回调投递到事件线程池。 */
	@Synchronized
	fun send(event: IJadxEvent) {
		val consumers = listeners[event.getType()]
		if (consumers != null) {
			for (consumer in consumers) {
				eventsThreadPool.execute { consumer.accept(event) }
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

	companion object {
		/** 创建带递增编号的守护线程工厂。 */
		private fun makeThreadFactory(): ThreadFactory = object : ThreadFactory {
			private val threadNumber = AtomicInteger(0)

			override fun newThread(r: Runnable): Thread = Thread(r, "jadx-events-thread-" + threadNumber.incrementAndGet())
		}
	}
}
