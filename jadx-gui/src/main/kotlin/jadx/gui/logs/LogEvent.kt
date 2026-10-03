package jadx.gui.logs

import ch.qos.logback.classic.Level

/**
 * 一条被 GUI 采集到的日志事件。
 *
 * **做什么**：把 logback 的 [Level]、logger 名称和已排版好的消息封装成不可变对象，
 * 供 [LogCollector] 缓冲并分发给各监听器。
 *
 * **为什么不是 `data class`**：原 Java 类只有 `toString()`，没有 `equals/hashCode`；
 * 保持普通类可避免自动生成的值语义改变调用方行为。
 *
 * 属性 [level] / [loggerName] / [msg] 会生成 `getLevel()` / `getLoggerName()` / `getMsg()`，
 * 与原 Java 的 JVM 方法名一致。
 */
class LogEvent internal constructor(
	val level: Level,
	val loggerName: String,
	val msg: String,
) {

	override fun toString(): String = "$level: $loggerName - $msg"
}
