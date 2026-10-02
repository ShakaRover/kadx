package jadx.core.plugins.events

import jadx.api.plugins.events.IJadxEvent
import jadx.api.plugins.events.IJadxEvents
import jadx.api.plugins.events.JadxEventType
import jadx.core.Consts
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * [IJadxEvents] 的默认实现：把调用转发给 [JadxEventsManager]。
 *
 * **做什么**：对上层提供事件总线接口，对下层委托给真正的发送/监听管理器，
 * 并在 [Consts.DEBUG_EVENTS] 打开时输出调试日志。
 *
 * **为什么保持 Java 可实现**：jadx-gui 的 `JadxGuiEventsImpl` 直接 `new JadxEventsImpl()`，
 * 因此构造器与公共方法签名保持与原 Java 一致。
 */
class JadxEventsImpl : IJadxEvents {

	private val manager = JadxEventsManager()

	override fun send(event: IJadxEvent) {
		if (Consts.DEBUG_EVENTS) {
			LOG.debug("Sending event: {}", event)
		}
		manager.send(event)
	}

	override fun <E : IJadxEvent> addListener(eventType: JadxEventType<E>, listener: Consumer<E>) {
		manager.addListener(eventType, listener)
		if (Consts.DEBUG_EVENTS) {
			LOG.debug("add listener for: {}, stats: {}", eventType, manager.listenersDebugStats())
		}
	}

	override fun <E : IJadxEvent> removeListener(eventType: JadxEventType<E>, listener: Consumer<E>) {
		manager.removeListener(eventType, listener)
		if (Consts.DEBUG_EVENTS) {
			LOG.debug("remove listener for: {}, stats: {}", eventType, manager.listenersDebugStats())
		}
	}

	override fun reset() {
		manager.reset()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxEventsImpl::class.java)
	}
}
