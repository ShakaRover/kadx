package jadx.gui.logs

import ch.qos.logback.classic.Level
import jadx.gui.ui.panel.IssuesPanel
import jadx.gui.utils.rx.DebounceUpdate
import javax.swing.SwingUtilities

/**
 * 统计日志中的错误/警告数量，并驱动「问题」面板刷新。
 *
 * **做什么**：每次收到日志就累加 ERROR/WARN 计数；由于日志可能非常密集，
 * 用 [DebounceUpdate] 把 500ms 内的多次变化合并成一次 UI 刷新。
 *
 * **线程模型**：保持原 Swing 模型——[DebounceUpdate] 的回调最终通过
 * `SwingUtilities.invokeLater` 切回 EDT 调用 [IssuesPanel.onUpdate]。
 */
class IssuesListener(private val issuesPanel: IssuesPanel) : ILogListener {

	private val updater: DebounceUpdate = DebounceUpdate(500, Runnable { onUpdate() })

	private var errors = 0
	private var warnings = 0

	private fun onUpdate() {
		SwingUtilities.invokeLater { issuesPanel.onUpdate(errors, warnings) }
	}

	override fun onAppend(logEvent: LogEvent) {
		// Level.toInt() 与 Level.ERROR_INT / WARN_INT 是 logback 的级别数值约定
		when (logEvent.level.toInt()) {
			Level.ERROR_INT -> {
				errors++
				updater.requestUpdate()
			}

			Level.WARN_INT -> {
				warnings++
				updater.requestUpdate()
			}
		}
	}

	override fun onReload() {
		errors = 0
		warnings = 0
		updater.requestUpdate()
	}

	fun getErrors(): Int = errors

	fun getWarnings(): Int = warnings
}
