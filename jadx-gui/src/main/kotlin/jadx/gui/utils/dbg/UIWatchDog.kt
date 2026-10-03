package jadx.gui.utils.dbg

import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.utils.UiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.SwingUtilities

/**
 * UI 线程卡顿监视器。
 *
 * **做什么**：在后台协程周期检查 EDT 是否长时间处于 `TIMED_WAITING`，
 * 若是则打印堆栈，帮助定位 UI 卡死。
 *
 * **线程模型（N1e 协程化）**：原「单线程 Executor + `Thread.sleep` 轮询」改为
 * 绑定 [scope] 的协程 + [delay]；[toggleState] 通过取消 [Job] 停止轮询。
 */
class UIWatchDog private constructor() {

	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private var job: Job? = null

	private fun toggleState(uiThread: Thread) {
		val current = job
		if (current != null && current.isActive) {
			// 停止
			current.cancel()
			job = null
		} else {
			// 启动
			job = scope.launch { start(uiThread) }
		}
	}

	private val isEnabled: Boolean get() = job?.isActive == true

	private suspend fun start(uiThread: Thread) {
		LOG.debug("UI watchdog started")
		try {
			val e = JadxRuntimeException("at")
			val tm = TimeMeasure()
			var stuck = false
			var reportTime = 0L
			while (currentCoroutineContext().isActive) {
				if (uiThread.state == Thread.State.TIMED_WAITING) {
					if (!stuck) {
						tm.start()
						stuck = true
						reportTime = UI_MAX_DELAY_MS.toLong()
					} else {
						tm.end()
						val time = tm.time
						if (time > reportTime) {
							e.setStackTrace(uiThread.getStackTrace())
							LOG.warn("UI events thread stuck for {}ms", time, e)
							reportTime += UI_MAX_DELAY_MS
						}
					}
				} else {
					stuck = false
				}
				delay(CHECK_INTERVAL_MS.toLong())
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			LOG.error("UI watchdog fail", e)
		} finally {
			LOG.debug("UI watchdog stopped")
		}
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

		val time: Long get() = end - start
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(UIWatchDog::class.java)

		private const val UI_MAX_DELAY_MS = 200
		private const val CHECK_INTERVAL_MS = 50

		private val INSTANCE = UIWatchDog()

		/** 启动监视（若尚未启用），返回当前启用状态。 */
		fun onStart(): Boolean {
			UiUtils.uiRunAndWait(Runnable { toggle() })
			return INSTANCE.isEnabled
		}

		/** 切换监视开关；必须在 UI 线程调用。 */
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
