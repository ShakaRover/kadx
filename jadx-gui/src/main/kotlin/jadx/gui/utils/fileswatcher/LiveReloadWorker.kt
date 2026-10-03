package jadx.gui.utils.fileswatcher

import io.reactivex.rxjava3.processors.PublishProcessor
import jadx.gui.ui.MainWindow
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 实时重载（live reload）工作器。
 *
 * **做什么**：监视项目输入文件，防抖 1 秒后在 EDT 上触发 [MainWindow.reopen]，
 * 实现“文件改动后自动重新反编译”。
 *
 * **线程模型**：保留原 Java 的“单线程 Executor + [FilesWatcher] 阻塞循环 + RxJava 防抖”，
 * 不引入协程。
 */
class LiveReloadWorker(private val mainWindow: MainWindow) {

	private val processor: PublishProcessor<Path> = PublishProcessor.create()

	@Volatile
	private var started = false
	private var executor: ExecutorService? = null
	private var watcher: FilesWatcher? = null

	init {
		processor
			.debounce(1L, TimeUnit.SECONDS)
			.subscribe {
				LOG.debug("Reload triggered")
				UiUtils.uiRun(Runnable { mainWindow.reopen() })
			}
	}

	fun isStarted(): Boolean = started

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

	private fun onUpdate(path: Path, pathKind: WatchEvent.Kind<Path>) {
		LOG.debug("Path updated: {}", path)
		processor.onNext(path)
	}

	@Synchronized
	private fun start() {
		try {
			watcher = FilesWatcher(mainWindow.getProject().getFilePaths(), this::onUpdate)
			executor = Executors.newSingleThreadExecutor()
			started = true
			val currentWatcher = watcher
			val currentExecutor = executor
			if (currentWatcher != null && currentExecutor != null) {
				currentExecutor.submit(Runnable { currentWatcher.watch() })
			}
		} catch (e: Exception) {
			LOG.warn("Failed to start live reload worker", e)
			resetState()
		}
	}

	@Synchronized
	private fun stop() {
		try {
			watcher?.cancel()
			executor?.shutdownNow()
			val canceled = executor?.awaitTermination(5L, TimeUnit.SECONDS) ?: false
			if (!canceled) {
				LOG.warn("Failed to cancel live reload worker")
			}
		} catch (e: Exception) {
			LOG.warn("Failed to stop live reload worker", e)
		} finally {
			resetState()
		}
	}

	private fun resetState() {
		started = false
		executor = null
		watcher = null
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(LiveReloadWorker::class.java)
	}
}
