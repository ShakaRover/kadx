package kadx.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import kadx.api.KadxDecompiler
import org.slf4j.LoggerFactory

/**
 * 日志级别控制工具。
 *
 * **做什么**：把命令行参数（`--quiet` / `--verbose` / `--log-level`）映射到 logback 的
 * [Level]，并允许对特定类/包单独设置级别（例如显示进度时把控制类调到 INFO）。
 *
 * **为什么这样写**：原 Java 全是静态方法，调用方（kadx-gui 等）以 `LogHelper.xxx(...)`
 * 调用，因此统一放进 `companion object`。
 */
class LogHelper {

	/**
	 * CLI 日志级别枚举。
	 *
	 * `PROGRESS` 在 logback 层面也是 OFF，但会额外打开控制类的 INFO 日志来显示进度。
	 */
	enum class LogLevelEnum(val level: Level) {
		QUIET(Level.OFF),
		PROGRESS(Level.OFF),
		ERROR(Level.ERROR),
		WARN(Level.WARN),
		INFO(Level.INFO),
		DEBUG(Level.DEBUG),
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(LogHelper::class.java)

		/** 当前日志级别；为 null 表示用户自定义了 logback 配置，kadx 不再干预。 */
		private var logLevelValue: LogLevelEnum? = null

		/** 根据命令行参数初始化日志级别。 */
		fun initLogLevel(args: KadxCLIArgs) {
			logLevelValue = getLogLevelFromArgs(args)
		}

		private fun getLogLevelFromArgs(args: KadxCLIArgs): LogLevelEnum? {
			if (isCustomLogConfig()) {
				return null
			}
			if (args.quiet) {
				args.logLevel = LogLevelEnum.QUIET
			} else if (args.verbose) {
				args.logLevel = LogLevelEnum.DEBUG
			}
			return args.logLevel
		}

		/** 设置并立即应用日志级别。 */
		fun setLogLevel(newLogLevel: LogLevelEnum) {
			logLevelValue = newLogLevel
			applyLogLevel(newLogLevel)
		}

		/** 重新应用当前日志级别（例如初始化完成后调用）。 */
		fun applyLogLevels() {
			val level = logLevelValue ?: return
			applyLogLevel(level)
			if (level == LogLevelEnum.PROGRESS) {
				fixForShowProgress()
			}
		}

		/**
		 * 显示进度：把控制类调到 INFO，避免进度输出被 QUIET 级别屏蔽。
		 */
		private fun fixForShowProgress() {
			setLevelForClass(KadxCLI::class.java, Level.INFO)
			setLevelForClass(KadxDecompiler::class.java, Level.INFO)
			setLevelForClass(SingleClassMode::class.java, Level.INFO)

			// 输入插件的警告与错误仍需要显示
			setLevelForPackage("kadx.plugins.input", Level.WARN)
		}

		private fun applyLogLevel(logLevel: LogLevelEnum) {
			val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
			rootLogger.level = logLevel.level
		}

		/** 返回当前日志级别；自定义 logback 配置时为 null。 */
		val logLevel: LogLevelEnum? get() = logLevelValue

		/** 为指定类单独设置日志级别。 */
		fun setLevelForClass(cls: Class<*>, level: Level) {
			(LoggerFactory.getLogger(cls) as Logger).level = level
		}

		/** 为指定包单独设置日志级别。 */
		fun setLevelForPackage(pkgName: String, level: Level) {
			(LoggerFactory.getLogger(pkgName) as Logger).level = level
		}

		/**
		 * 检测用户是否通过 `-Dlogback.configurationFile=` 提供了自定义 logback 配置。
		 */
		private fun isCustomLogConfig(): Boolean {
			try {
				val logbackConfig = System.getProperty("logback.configurationFile")
				if (logbackConfig == null) {
					return false
				}
				LOG.debug("Use custom log config: {}", logbackConfig)
				return true
			} catch (e: Exception) {
				LOG.error("Failed to detect custom log config", e)
			}
			return false
		}
	}
}
