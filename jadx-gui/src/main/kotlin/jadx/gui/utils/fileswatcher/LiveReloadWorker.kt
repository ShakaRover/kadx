package jadx.gui.utils.fileswatcher

import jadx.gui.ui.MainWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * 实时重载（live reload）工作器。
 *
 * **做什么**：监视项目输入文件，防抖 1 秒后在 EDT 上触发 [MainWindow.reopen]，
 * 实现“文件改动后自动重新反编译”。
 *
 * **线程模型（N1d 协程化）**：用 [FilesWatcher.watchEvents] 的 Flow 在
 * `Dispatchers.IO` 上收集事件，防抖后通过 `Dispatchers.Swing` 回到 EDT 重载；
 * [stop] 取消 [scope]，从而中断监视循环。
 */
@OptIn(FlowPreview::class)
class LiveReloadWorker(private val mainWindow: MainWindow) {

	@Volatile
	private var started = false
	private var scope: CoroutineScope? = null

	val isStarted: Boolean get() = started

	/** 根据开关状态启动/停止监视。 */
	@Synchronized
	fun updateState(enabled: Boolean) {
		if (this.started == enabled) {
			return
		}
		if (enabled) {
			LOG.debug("Starting live reload worker")
			start()
		} else {
			LOG.debug("Stopping live reload worker")
			stop()
		}
	}

	@Synchronized
	private fun start() {
		try {
			val watcher = FilesWatcher(mainWindow.getProject().filePaths)
			val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
			newScope.launch {
				watcher.watchEvents()
					.debounce(RELOAD_DEBOUNCE_MS.milliseconds)
					.collect {
						LOG.debug("Reload triggered")
						withContext(Dispatchers.Swing) { mainWindow.reopen() }
					}
			}
			scope = newScope
			started = true
		} catch (e: Exception) {
			LOG.warn("Failed to start live reload worker", e)
			resetState()
		}
	}

	@Synchronized
	private fun stop() {
		try {
			// 取消作用域会中断监视循环并关闭 WatchService
			scope?.cancel()
		} catch (e: Exception) {
			LOG.warn("Failed to stop live reload worker", e)
		} finally {
			resetState()
		}
	}

	private fun resetState() {
		started = false
		scope = null
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(LiveReloadWorker::class.java)

		/** 重载防抖窗口（毫秒）。 */
		private const val RELOAD_DEBOUNCE_MS = 1000L
	}
}
