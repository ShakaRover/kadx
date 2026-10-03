package jadx.gui.jobs

import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.gui.ui.panel.ProgressPanel
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * 后台任务执行器：串行调度任务并驱动进度条。
 *
 * **做什么**：内部维护一个**单线程** [ThreadPoolExecutor]，所有任务按提交顺序执行
 * （任务内部再通过 [jadx.api.utils.tasks.ITaskExecutor] 并行化）。每个任务都被包装成
 * [InternalTask] 交给 [ProgressUpdater] 刷新进度，并在结束时触发 `onDone` / `onFinish`。
 *
 * **线程模型**（阶段 5.1 保持原 Swing 线程模型，不引入协程）：
 * - 任务调度与取消检查在 [taskQueueExecutor] 线程上进行；
 * - `onFinish` 通过 [UiUtils.uiRunAndWait] 在 EDT 上执行，保证 Swing 安全；
 * - 取消/超时/内存检查由 [buildCancelCheck] 生成的 [Supplier] 周期性触发。
 *
 * **实例来源**：[MainWindow] 持有一个实例，所有 GUI 后台操作都经它提交。
 */
class BackgroundExecutor(
	private val settings: JadxSettings,
	progressPane: ProgressPanel,
) {
	private val progressUpdater: ProgressUpdater = ProgressUpdater(progressPane) { taskCanceled(it) }

	/** 单线程任务队列；在 [reset] 中创建，故使用 `lateinit`。 */
	private lateinit var taskQueueExecutor: ThreadPoolExecutor

	/** 正在运行（或已提交）的任务，键为自增编号。 */
	private val taskRunning: MutableMap<Long, InternalTask> = ConcurrentHashMap()

	/** 任务编号生成器，每次 [reset] 归零。 */
	private val idSupplier = AtomicLong(0)

	init {
		reset()
	}

	/** 提交并异步执行一个后台任务。 */
	@Synchronized
	fun execute(task: IBackgroundTask) {
		val internalTask = buildTask(task)
		taskQueueExecutor.execute { runTask(internalTask) }
	}

	/** 提交任务并返回其完成后的状态 [Future]。 */
	@Synchronized
	fun executeWithFuture(task: IBackgroundTask): Future<TaskStatus> {
		val internalTask = buildTask(task)
		return taskQueueExecutor.submit(
			Callable {
				runTask(internalTask)
				internalTask.getStatus()
			},
		)
	}

	/** 取消所有正在运行的任务并重建执行器。 */
	@Synchronized
	fun cancelAll() {
		try {
			taskRunning.values.forEach { cancelTask(it) }
			taskQueueExecutor.shutdownNow()
			val complete = taskQueueExecutor.awaitTermination(3, TimeUnit.SECONDS)
			if (complete) {
				LOG.debug("Background task executor canceled successfully")
			} else {
				val taskNames = taskRunning.values.joinToString(", ") { it.getBgTask().getTitle() }
				LOG.debug("Background task executor cancel failed. Running tasks: {}", taskNames)
			}
		} catch (e: Exception) {
			LOG.error("Error terminating task executor", e)
		} finally {
			reset()
		}
	}

	/** 阻塞等待队列中所有任务执行完成。 */
	@Synchronized
	fun waitForComplete() {
		try {
			// 提交一个空任务，等待其完成即代表队列已清空
			taskQueueExecutor.submit(UiUtils.EMPTY_RUNNABLE).get()
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to wait tasks completion", e)
		}
	}

	/** 用一组 job 构造 [SimpleTask] 并执行，完成后在 EDT 回调。 */
	fun execute(title: String, backgroundJobs: List<Runnable>, onFinishUiRunnable: Consumer<TaskStatus>) {
		execute(SimpleTask(title, backgroundJobs, onFinishUiRunnable))
	}

	/** 用单个 job 构造 [SimpleTask] 并执行，完成后在 EDT 回调。 */
	fun execute(title: String, backgroundRunnable: Runnable, onFinishUiRunnable: Consumer<TaskStatus>) {
		execute(SimpleTask(title, listOf(backgroundRunnable), onFinishUiRunnable))
	}

	/** 用单个 job 构造 [SimpleTask] 并执行。 */
	fun execute(title: String, backgroundRunnable: Runnable) {
		execute(SimpleTask(title, backgroundRunnable))
	}

	/** 以“加载中”标题执行一个后台 job，完成后在 EDT 回调。 */
	fun startLoading(backgroundRunnable: Runnable, onFinishUiRunnable: Runnable) {
		execute(SimpleTask(NLS.str("progress.load"), backgroundRunnable, onFinishUiRunnable))
	}

	/** 以“加载中”标题执行一个后台 job。 */
	fun startLoading(backgroundRunnable: Runnable) {
		execute(SimpleTask(NLS.str("progress.load"), backgroundRunnable))
	}

	/** 重建单线程执行器并清空任务表与编号。 */
	@Synchronized
	private fun reset() {
		taskQueueExecutor = Executors.newFixedThreadPool(1, Utils.simpleThreadFactory("bg")) as ThreadPoolExecutor
		taskRunning.clear()
		idSupplier.set(0)
	}

	/** 为任务分配编号并登记到运行表。 */
	private fun buildTask(task: IBackgroundTask): InternalTask {
		val id = idSupplier.incrementAndGet()
		val internalTask = InternalTask(id, task)
		taskRunning[id] = internalTask
		return internalTask
	}

	/** 任务主体：准备执行器、注册进度、执行并等待结束。 */
	private fun runTask(internalTask: InternalTask) {
		try {
			val task = internalTask.getBgTask()
			val taskExecutor = task.scheduleTasks()
			taskExecutor.setThreadsCount(settings.getThreadsCount())
			val tasksCount = taskExecutor.getTasksCount()
			internalTask.setTaskExecutor(taskExecutor)
			internalTask.setJobsCount(tasksCount.toLong())
			if (UiUtils.JADX_GUI_DEBUG) {
				LOG.debug(
					"Starting background task '{}', jobs count: {}, time limit: {} ms, memory check: {}",
					task.getTitle(),
					tasksCount,
					task.timeLimit(),
					task.checkMemoryUsage(),
				)
			}
			val startTime = System.currentTimeMillis()
			val cancelCheck = buildCancelCheck(internalTask, startTime)
			internalTask.taskStart(startTime, cancelCheck)
			progressUpdater.addTask(internalTask)
			taskExecutor.execute()
			taskExecutor.awaitTermination()
		} catch (e: Exception) {
			LOG.error("Task failed", e)
			internalTask.setStatus(TaskStatus.ERROR)
		} finally {
			taskComplete(internalTask)
		}
	}

	/** 任务收尾：统计进度、触发 `onDone`（后台）与 `onFinish`（EDT），最后清理。 */
	private fun taskComplete(internalTask: InternalTask) {
		try {
			val task = internalTask.getBgTask()
			internalTask.setJobsComplete(checkNotNull(internalTask.getTaskExecutor()).getProgress().toLong())
			internalTask.setStatus(TaskStatus.COMPLETE)
			internalTask.updateExecTime()
			task.onDone(internalTask)
			// 把 UI 任务操作视作任务的一部分，避免与其他任务交错
			UiUtils.uiRunAndWait {
				try {
					task.onFinish(internalTask)
				} catch (e: Exception) {
					LOG.error("Task onFinish failed", e)
					internalTask.setStatus(TaskStatus.ERROR)
				}
			}
		} catch (e: Exception) {
			LOG.error("Task complete failed", e)
			internalTask.setStatus(TaskStatus.ERROR)
		} finally {
			internalTask.taskComplete()
			progressUpdater.taskComplete(internalTask)
			removeTask(internalTask)
		}
	}

	private fun removeTask(internalTask: InternalTask) {
		taskRunning.remove(internalTask.getId())
	}

	/** 取消单个任务：先请求取消，再在超时后强制关闭其内部线程池。 */
	private fun cancelTask(internalTask: InternalTask) {
		try {
			val task = internalTask.getBgTask()
			if (!internalTask.isRunning()) {
				// 任务已完成或尚未开始
				task.cancel()
				removeTask(internalTask)
				return
			}
			val taskExecutor = checkNotNull(internalTask.getTaskExecutor())
			// 请求强制终止
			task.cancel()
			taskExecutor.terminate()

			val executor = taskExecutor.getInternalExecutor() ?: return
			val cancelTimeout = task.getCancelTimeoutMS()
			if (cancelTimeout != 0) {
				if (executor.awaitTermination(cancelTimeout.toLong(), TimeUnit.MILLISECONDS)) {
					LOG.debug("Task cancel complete")
					return
				}
			}
			LOG.debug("Forcing tasks cancel")
			executor.shutdownNow()
			val complete = executor.awaitTermination(task.getShutdownTimeoutMS().toLong(), TimeUnit.MILLISECONDS)
			LOG.debug(
				"Forced task cancel status: {}",
				if (complete) {
					"success"
				} else {
					"fail, still active: " + (taskExecutor.getTasksCount() - taskExecutor.getProgress())
				},
			)
		} catch (e: Exception) {
			LOG.error("Failed to cancel task: {}", internalTask, e)
		}
	}

	/** 进度刷新器发来的取消通知。 */
	private fun taskCanceled(task: InternalTask) {
		cancelTask(task)
	}

	/**
	 * 构造取消检查函数：返回非 null 表示应取消。
	 *
	 * 检查顺序：用户取消/线程中断 → 超时 → 内存不足（先降线程数并 GC，仍不足才取消）。
	 */
	private fun buildCancelCheck(internalTask: InternalTask, startTime: Long): Supplier<TaskStatus?> {
		val task = internalTask.getBgTask()
		val timeLimit = task.timeLimit()
		val waitUntilTime = if (timeLimit == 0) 0L else startTime + timeLimit
		val checkMemoryUsage = task.checkMemoryUsage()
		return Supplier {
			if (task.isCanceled() || Thread.currentThread().isInterrupted) {
				return@Supplier TaskStatus.CANCEL_BY_USER
			}
			if (waitUntilTime != 0L && waitUntilTime < System.currentTimeMillis()) {
				LOG.warn("Task '{}' execution timeout, force cancel", task.getTitle())
				return@Supplier TaskStatus.CANCEL_BY_TIMEOUT
			}
			if (checkMemoryUsage && !UiUtils.isFreeMemoryAvailable()) {
				LOG.warn("High memory usage: {}", UiUtils.memoryInfo())
				if (checkNotNull(internalTask.getTaskExecutor()).getThreadsCount() == 1) {
					LOG.warn("Task '{}' memory limit reached, force cancel", task.getTitle())
					return@Supplier TaskStatus.CANCEL_BY_MEMORY
				}
				LOG.warn("Low free memory, reduce processing threads count to 1")
				// 降低线程数后继续执行
				checkNotNull(internalTask.getTaskExecutor()).setThreadsCount(1)
				System.gc()
				UiUtils.sleep(1000) // 等待 GC
				if (!UiUtils.isFreeMemoryAvailable()) {
					LOG.error("Task '{}' memory limit reached (after GC), force cancel", task.getTitle())
					return@Supplier TaskStatus.CANCEL_BY_MEMORY
				}
			}
			null
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(BackgroundExecutor::class.java)
	}
}
