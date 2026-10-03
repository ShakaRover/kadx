package jadx.gui.jobs

import jadx.core.utils.Utils
import jadx.gui.ui.panel.ProgressPanel
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.DelayQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * 后台任务的进度刷新器。
 *
 * **做什么**：维护一个 [DelayQueue]，按固定间隔（[UPDATE_INTERVAL_MS]）取出运行中的
 * [InternalTask]，在 EDT 上更新 [ProgressPanel]，并周期性执行取消检查。
 *
 * **线程模型**（阶段 5.1 保持原 Swing 线程模型，不引入协程）：
 * - 独立单线程 [bgExecutor] 运行 [updateLoop]；
 * - UI 更新通过 [UiUtils.uiRun] 投递到 EDT；
 * - 取消回调 [cancelCallback] 在刷新线程上调用。
 *
 * **为什么字段是 private 但保留**：`bgExecutor` 与 `tasks` 的生命周期贯穿整个
 * 更新器，不能被回收；`tasks` 必须在启动刷新线程前完成初始化。
 */
class ProgressUpdater(
	private val progressPane: ProgressPanel,
	private val cancelCallback: Consumer<InternalTask>,
) {
	/** 刷新线程池（单线程，守护线程工厂）。 */
	private val bgExecutor: ExecutorService =
		Executors.newSingleThreadExecutor(Utils.simpleThreadFactory("jadx-progress"))

	/** 待刷新任务队列，按 `nextUpdate` 时间排序。 */
	private val tasks: BlockingQueue<InternalTask> = DelayQueue()

	init {
		// 启动后台刷新循环
		bgExecutor.execute { updateLoop() }
	}

	/** 添加任务到刷新队列；静默任务不显示进度，直接跳过。 */
	fun addTask(task: InternalTask) {
		if (task.getBgTask().isSilent()) {
			return
		}
		scheduleNextUpdate(task)
	}

	/** 任务完成：清零下次刷新时间并立即刷新一次（使进度面板复位/隐藏）。 */
	fun taskComplete(task: InternalTask) {
		task.setNextUpdate(0)
		updateProgress(task)
	}

	private fun scheduleNextUpdate(task: InternalTask) {
		task.setNextUpdate(System.currentTimeMillis() + UPDATE_INTERVAL_MS)
		tasks.add(task)
	}

	/** 刷新循环：阻塞等待到期的任务，刷新进度、检查取消，然后重新排期。 */
	private fun updateLoop() {
		while (true) {
			try {
				val task = tasks.take()
				if (task.isRunning()) {
					updateProgress(task)
					cancelCheck(task)
					scheduleNextUpdate(task)
				}
			} catch (e: Exception) {
				LOG.warn("Error in ProgressUpdater loop", e)
			}
		}
	}

	private fun updateProgress(internalTask: InternalTask) {
		UiUtils.uiRun {
			val bgTask = internalTask.getBgTask()
			if (internalTask.isRunning()) {
				if (internalTask.checkForFirstUpdate()) {
					progressPane.setLabel(bgTask.getTitle() + "… ")
					progressPane.setCancelButtonVisible(bgTask.canBeCanceled())
					progressPane.setVisible(true)
				}
				val customProgress = bgTask.getTaskProgress()
				val taskProgress = customProgress ?: TaskProgress(
					checkNotNull(internalTask.getTaskExecutor()).getProgress().toLong(),
					internalTask.getJobsCount(),
				)
				progressPane.setProgress(taskProgress)
				val onProgressListener = bgTask.getProgressListener()
				if (onProgressListener != null) {
					onProgressListener.accept(taskProgress)
				}
			} else {
				progressPane.reset()
				progressPane.setVisible(false)
			}
		}
	}

	/** 执行取消检查：若检查函数返回非 null，则设置状态、更新 UI 并触发取消回调。 */
	private fun cancelCheck(task: InternalTask) {
		val taskStatus = task.getCancelCheck().get()
		if (taskStatus == null) {
			return
		}
		task.setStatus(taskStatus)
		UiUtils.uiRun {
			val bgTask = task.getBgTask()
			progressPane.setLabel(bgTask.getTitle() + " (" + NLS.str("progress.canceling") + ")… ")
			progressPane.setCancelButtonVisible(false)
			progressPane.setIndeterminate(true)
		}
		cancelCallback.accept(task)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ProgressUpdater::class.java)

		/** 进度刷新间隔（毫秒）。 */
		private const val UPDATE_INTERVAL_MS = 1000
	}
}
