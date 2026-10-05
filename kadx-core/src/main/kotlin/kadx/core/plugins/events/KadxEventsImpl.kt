package kadx.core.plugins.events

import kadx.api.plugins.events.IKadxEvent
import kadx.api.plugins.events.IKadxEvents
import kadx.api.plugins.events.KadxEventType
import kadx.core.Consts
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * [IKadxEvents] 的默认实现：把调用转发给 [KadxEventsManager]。
 *
 * **做什么**：对上层提供事件总线接口，对下层委托给真正的发送/监听管理器，
 * 并在 [Consts.DEBUG_EVENTS] 打开时输出调试日志。
 *
 * **为什么保持 Java 可实现**：kadx-gui 的 `KadxGuiEventsImpl` 直接 `new KadxEventsImpl()`，
 * 因此构造器与公共方法签名保持与原 Java 一致。
 */
class KadxEventsImpl : IKadxEvents {

	private val manager = KadxEventsManager()

	override fun send(event: IKadxEvent) {
		if (Consts.DEBUG_EVENTS) {
			LOG.debug("Sending event: {}", event)
		}
		manager.send(event)
	}

	override fun <E : IKadxEvent> addListener(eventType: KadxEventType<E>, listener: Consumer<E>) {
		manager.addListener(eventType, listener)
		if (Consts.DEBUG_EVENTS) {
			LOG.debug("add listener for: {}, stats: {}", eventType, manager.listenersDebugStats())
		}
	}

	override fun <E : IKadxEvent> removeListener(eventType: KadxEventType<E>, listener: Consumer<E>) {
		manager.removeListener(eventType, listener)
		if (Consts.DEBUG_EVENTS) {
			LOG.debug("remove listener for: {}, stats: {}", eventType, manager.listenersDebugStats())
		}
	}

	override fun reset() {
		manager.reset()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxEventsImpl::class.java)
	}
}
