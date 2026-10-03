package jadx.gui.logs

import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.utils.UiUtils
import org.apache.commons.lang3.StringUtils
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea

/**
 * 把日志事件追加到代码区（[RSyntaxTextArea]）的监听器。
 *
 * **做什么**：按 [LogOptions] 过滤日志（级别 + 模式），命中的消息在 EDT 上追加到文本区；
 * [onReload] 则打印一条分隔线。
 *
 * **线程模型**：保持原 Swing 模型——追加操作分别通过 [UiUtils.uiRun] / [UiUtils.uiRunAndWait]
 * 切到 EDT。
 */
internal class LogAppender(
	private val options: LogOptions,
	private val textArea: RSyntaxTextArea,
) : ILogListener {

	override fun onAppend(logEvent: LogEvent) {
		if (accept(logEvent)) {
			UiUtils.uiRun { textArea.append(logEvent.msg) }
		}
	}

	override fun onReload() {
		UiUtils.uiRunAndWait { textArea.append(StringUtils.repeat('=', 100) + '\n') }
	}

	/** 判断某条日志是否应显示：先比级别，再按模式匹配 logger 名。 */
	private fun accept(logEvent: LogEvent): Boolean {
		val byLevel = logEvent.level.isGreaterOrEqual(options.logLevel)
		if (!byLevel) {
			return false
		}
		return when (options.mode) {
			LogMode.ALL -> true
			LogMode.ALL_SCRIPTS -> logEvent.loggerName.startsWith("JadxScript:")
			LogMode.CURRENT_SCRIPT -> logEvent.loggerName == options.filter
			else -> throw JadxRuntimeException("Unexpected log mode: " + options.mode)
		}
	}
}
