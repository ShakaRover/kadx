package jadx.gui.logs

import ch.qos.logback.classic.Level
import jadx.core.utils.Utils

/**
 * 日志查看器的过滤选项（模式 + 级别 + 脚本过滤串）。
 *
 * **做什么**：GUI 中多处会请求「以某个级别/模式打开日志」，这里集中保存最近一次请求，
 * 并通过 [current] 让后续只调整部分字段的请求可以继承其余字段。
 *
 * **Kotlin 转换说明**：原 Java 的静态字段 `current` 与静态方法 `current()` 同名，
 * Kotlin 不允许属性与函数同名，故字段改名为 [currentOptions]（对外行为不变）。
 */
class LogOptions private constructor(
	val mode: LogMode,
	val logLevel: Level,
	val filter: String?,
) {

	override fun toString(): String = "LogOptions{mode=$mode, logLevel=$logLevel, filter='$filter'}"

	companion object {
		/** 最近一次被请求的日志选项。 */
		private var currentOptions: LogOptions = LogOptions(LogMode.ALL, Level.INFO, null)

		/** 以 [LogMode.ALL] 模式打开；[logLevel] 为空时沿用当前级别。 */
		@JvmStatic
		fun allWithLevel(logLevel: Level?): LogOptions {
			val level = Utils.getOrElse(logLevel, currentOptions.logLevel)
			return store(LogOptions(LogMode.ALL, level, null))
		}

		/** 只修改日志级别，模式与脚本过滤沿用当前值。 */
		@JvmStatic
		fun forLevel(logLevel: Level?): LogOptions {
			val level = Utils.getOrElse(logLevel, currentOptions.logLevel)
			return store(LogOptions(currentOptions.mode, level, currentOptions.filter))
		}

		/** 只修改过滤模式，级别与脚本过滤沿用当前值。 */
		@JvmStatic
		fun forMode(mode: LogMode): LogOptions = store(LogOptions(mode, currentOptions.logLevel, currentOptions.filter))

		/** 只看某个脚本的日志（logger 名固定为 `JadxScript:<脚本名>`）。 */
		@JvmStatic
		fun forScript(scriptName: String): LogOptions {
			val filter = "JadxScript:$scriptName"
			return store(LogOptions(LogMode.CURRENT_SCRIPT, currentOptions.logLevel, filter))
		}

		/** 返回最近一次请求的选项。 */
		@JvmStatic
		fun current(): LogOptions = currentOptions

		private fun store(logOptions: LogOptions): LogOptions {
			currentOptions = logOptions
			return logOptions
		}
	}
}
