package jadx.gui.logs

/**
 * 日志监听器接口。
 *
 * **做什么**：任何关心日志事件的组件（日志面板、问题计数面板等）实现本接口，
 * 由 [LogCollector] 在每次追加日志时回调 [onAppend]，在清空日志缓冲区时回调 [onReload]。
 *
 * **为什么保持接口形态**：原 Java 接口被多个类实现，方法名与 JVM 表面保持不变。
 */
interface ILogListener {

	/** 有新日志追加时回调（在写日志的线程上调用）。 */
	fun onAppend(logEvent: LogEvent)

	/** 日志缓冲区被重置时回调，用于清空已显示内容。 */
	fun onReload()
}
