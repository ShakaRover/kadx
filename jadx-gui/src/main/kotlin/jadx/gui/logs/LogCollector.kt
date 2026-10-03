package jadx.gui.logs

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.Layout
import org.slf4j.LoggerFactory
import java.util.Queue

/**
 * 全局日志收集器：作为 logback appender 挂到 root logger 上。
 *
 * **做什么**：把每条日志排版成文本后存入 [buffer]（容量 [BUFFER_SIZE]，超出丢弃最旧），
 * 再分发给所有已注册的 [ILogListener]（日志面板、问题计数面板等）。
 *
 * **为什么保持 [AppenderBase] 子类**：必须保留 logback 的 `append`/`start`/`setContext`
 * 生命周期与线程模型；`append` 在写日志线程上同步执行，因此仍加 `@Synchronized`。
 */
class LogCollector : AppenderBase<ILoggingEvent>() {

	/** 已注册的监听器列表。 */
	private val listeners: MutableList<ILogListener> = ArrayList()

	/** 最近日志的环形缓冲。 */
	private val buffer: Queue<LogEvent> = LimitedQueue(BUFFER_SIZE)

	/** logback 的日志排版器，由 [register] 注入。 */
	private lateinit var layout: Layout<ILoggingEvent>

	init {
		name = "LogCollector"
	}

	@Synchronized
	override fun append(event: ILoggingEvent) {
		val msg = layout.doLayout(event)
		val logEvent = LogEvent(event.level, event.loggerName, msg)
		buffer.offer(logEvent)
		listeners.forEach { it.onAppend(logEvent) }
	}

	@Synchronized
	fun registerListener(listener: ILogListener) {
		listeners.add(listener)
		// 先回放缓冲区中的历史日志，保证新监听器立即看到已有内容
		buffer.forEach { listener.onAppend(it) }
	}

	@Synchronized
	fun removeListener(listener: ILogListener?): Boolean {
		if (listener == null) {
			return false
		}
		return listeners.removeIf { it === listener }
	}

	@Synchronized
	fun removeListenerByClass(listenerCls: Class<*>): Boolean = listeners.removeIf { it.javaClass == listenerCls }

	/** 清空缓冲区并通知所有监听器重载。 */
	@Synchronized
	fun reset() {
		buffer.clear()
		listeners.forEach { it.onReload() }
	}

	companion object {
		const val BUFFER_SIZE: Int = 5000

		private val INSTANCE: LogCollector = LogCollector()

		val instance: LogCollector get() = INSTANCE

		/** 创建排版器并把单例挂到 root logger 上（应用启动时调用一次）。 */
		fun register() {
			val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
			val loggerContext: LoggerContext = rootLogger.getLoggerContext()

			val layout = PatternLayout()
			layout.setContext(loggerContext)
			layout.setPattern("%-5level: %msg%n")
			layout.start()

			INSTANCE.setContext(loggerContext)
			INSTANCE.layout = layout
			INSTANCE.start()

			rootLogger.addAppender(INSTANCE)
		}
	}
}
