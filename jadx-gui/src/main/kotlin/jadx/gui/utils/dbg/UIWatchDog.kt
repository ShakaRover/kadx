package jadx.gui.utils.dbg

import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * UI 线程卡顿监视器。
 *
 * **做什么**：在后台线程周期检查 EDT 是否长时间处于 `TIMED_WAITING`，
 * 若是则打印堆栈，帮助定位 UI 卡死。
 *
 * **线程模型**：保留原 Java 的“单线程 Executor + 原子开关”模型，不使用协程。
 */
class UIWatchDog private constructor() {

	private val enabled = AtomicBoolean(false)
	private val executor: ExecutorService = Executors.newSingleThreadExecutor()
	private var taskFuture: Future<*>? = null

	private fun toggleState(uiThread: Thread) {
		if (enabled.get()) {
			// 停止
			enabled.set(false)
			val future = taskFuture
			if (future != null) {
				try {
					future.get(CHECK_INTERVAL_MS * 5L, TimeUnit.MILLISECONDS)
				} catch (e: Throwable) {
					LOG.warn("Stopping UI watchdog error", e)
				}
			}
		} else {
			// 启动
			enabled.set(true)
			taskFuture = executor.submit(Runnable { start(uiThread) })
		}
	}

	private fun isEnabled(): Boolean = enabled.get()

	@Suppress("BusyWait")
	private fun start(uiThread: Thread) {
		LOG.debug("UI watchdog started")
		try {
			val e = JadxRuntimeException("at")
			val tm = TimeMeasure()
			var stuck = false
			var reportTime = 0L
			while (enabled.get()) {
				if (uiThread.getState() == Thread.State.TIMED_WAITING) {
					if (!stuck) {
						tm.start()
						stuck = true
						reportTime = UI_MAX_DELAY_MS.toLong()
					} else {
						tm.end()
						val time = tm.getTime()
						if (time > reportTime) {
							e.setStackTrace(uiThread.getStackTrace())
							LOG.warn("UI events thread stuck for {}ms", time, e)
							reportTime += UI_MAX_DELAY_MS
						}
					}
				} else {
					stuck = false
				}
				Thread.sleep(CHECK_INTERVAL_MS.toLong())
			}
		} catch (e: Throwable) {
			LOG.error("UI watchdog fail", e)
		}
		LOG.debug("UI watchdog stopped")
	}

	private class TimeMeasure {
		private var start = 0L
		private var end = 0L

		fun start() {
			start = System.currentTimeMillis()
		}

		fun end() {
			end = System.currentTimeMillis()
		}

		fun getTime(): Long = end - start
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(UIWatchDog::class.java)

		private const val UI_MAX_DELAY_MS = 200
		private const val CHECK_INTERVAL_MS = 50

		private val INSTANCE = UIWatchDog()

		/** 启动监视（若尚未启用），返回当前启用状态。 */
		@JvmStatic
		fun onStart(): Boolean {
			UiUtils.uiRunAndWait(Runnable { toggle() })
			return INSTANCE.isEnabled()
		}

		/** 切换监视开关；必须在 UI 线程调用。 */
		@JvmStatic
		@Synchronized
		fun toggle() {
			if (SwingUtilities.isEventDispatchThread()) {
				INSTANCE.toggleState(Thread.currentThread())
			} else {
				throw JadxRuntimeException("This method should be called in UI thread")
			}
		}
	}
}
