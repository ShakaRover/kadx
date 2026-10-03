package jadx.gui.jobs

import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.panel.ProgressPanel
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

/**
 * 后台任务执行器：串行调度任务并驱动进度条。
 *
 * **做什么**：所有任务在 [scope] 上以协程调度，通过 [taskMutex] 串行化执行
 * （任务内部再通过 [jadx.api.utils.tasks.ITaskExecutor] 并行化）。每个任务被包装成
 * [InternalTask] 交给 [ProgressUpdater] 刷新进度，并在结束时触发 `onDone` / `onFinish`。
 *
 * **线程模型（N1a 协程化）**：
 * - [scope] = `SupervisorJob() + Dispatchers.Swing`，因此协程默认在 EDT 上；
 * - 实际耗时工作通过 `withContext(Dispatchers.IO)` 下放到 IO 线程池；
 * - `onDone` 在 IO 上执行，`onFinish` 回到 EDT（[scope] 的调度器）；
 * - 取消通过 [Job] 与任务自身的 [Cancelable.cancel] 共同完成。
 *
 * **实例来源**：[MainWindow] 持有一个实例，所有 GUI 后台操作都经它提交。
 */
class BackgroundExecutor(
	private val settings: JadxSettings,
	progressPane: ProgressPanel,
) {
	/** 协程作用域：绑定到 [dispose] 的生命周期，禁止使用 GlobalScope。 */
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

	/** 串行化任务执行，替代原先的单线程执行器。 */
	private val taskMutex: Mutex = Mutex()

	private val progressUpdater: ProgressUpdater = ProgressUpdater(progressPane, scope) { taskCanceled(it) }

	/** 正在运行（或已提交）的任务，键为自增编号。 */
	private val taskRunning: MutableMap<Long, InternalTask> = ConcurrentHashMap()

	/** 任务编号生成器。 */
	private val idSupplier = AtomicLong(0)

	/** 提交并异步执行一个后台任务。 */
	@Synchronized
	fun execute(task: IBackgroundTask) {
		val internalTask = buildTask(task)
		internalTask.setJob(scope.launch { runTask(internalTask) })
	}

	/** 提交任务并返回其完成后的状态 [Deferred]。 */
	@Synchronized
	fun executeAsync(task: IBackgroundTask): Deferred<TaskStatus> {
		val internalTask = buildTask(task)
		val deferred = scope.async {
			runTask(internalTask)
			internalTask.status
		}
		internalTask.setJob(deferred)
		return deferred
	}

	/** 在后台执行器的作用域内启动一个协程（EDT 调度）。 */
	fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

	/** 取消所有正在运行/排队中的任务。 */
	fun cancelAll() {
		val tasks = synchronized(this) { taskRunning.values.toList() }
		tasks.forEach { cancelTask(it) }
		try {
			// 避免在 EDT 上阻塞等待（取消按钮可能从 EDT 触发）
			if (!SwingUtilities.isEventDispatchThread()) {
				runBlocking { withTimeoutOrNull(CANCEL_WAIT_MS) { taskMutex.withLock { } } }
			}
		} catch (e: Exception) {
			LOG.error("Error terminating task executor", e)
		} finally {
			synchronized(this) {
				taskRunning.clear()
				idSupplier.set(0)
			}
		}
	}

	/** 阻塞等待队列中所有任务执行完成。 */
	fun waitForComplete() {
		try {
			runBlocking { taskMutex.withLock { } }
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to wait tasks completion", e)
		}
	}

	/** 取消执行器作用域（应用关闭时调用）。 */
	fun dispose() {
		scope.cancel()
	}

	/** 用一组 job 构造 [SimpleTask] 并执行，完成后在 EDT 回调。 */
	fun execute(title: String, backgroundJobs: List<Runnable>, onFinishUiRunnable: (TaskStatus) -> Unit) {
		execute(SimpleTask(title, backgroundJobs, onFinishUiRunnable))
	}

	/** 用单个 job 构造 [SimpleTask] 并执行，完成后在 EDT 回调。 */
	fun execute(title: String, backgroundRunnable: Runnable, onFinishUiRunnable: (TaskStatus) -> Unit) {
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

	/** 为任务分配编号并登记到运行表。 */
	private fun buildTask(task: IBackgroundTask): InternalTask {
		val id = idSupplier.incrementAndGet()
		val internalTask = InternalTask(id, task)
		taskRunning[id] = internalTask
		return internalTask
	}

	/** 任务主体：串行获取锁，在 IO 上执行耗时工作，再回到 EDT 收尾。 */
	private suspend fun runTask(internalTask: InternalTask) {
		var locked = false
		try {
			taskMutex.lock()
			locked = true
			withContext(Dispatchers.IO) {
				runTaskBlocking(internalTask)
			}
			finishTask(internalTask)
		} catch (e: CancellationException) {
			if (!locked) {
				LOG.debug("Task canceled before start: {}", internalTask)
			}
		} catch (e: Exception) {
			LOG.error("Task failed", e)
			internalTask.setStatus(TaskStatus.ERROR)
		} finally {
			if (locked) {
				internalTask.taskComplete()
				progressUpdater.taskComplete(internalTask)
				removeTask(internalTask)
				taskMutex.unlock()
			} else {
				removeTask(internalTask)
			}
		}
	}

	/** 在 IO 线程上执行的阻塞式任务主体（准备执行器、执行并等待结束、触发 `onDone`）。 */
	private fun runTaskBlocking(internalTask: InternalTask) {
		try {
			val task = internalTask.getBgTask()
			val taskExecutor = task.scheduleTasks()
			taskExecutor.setThreadsCount(settings.threadsCount)
			val tasksCount = taskExecutor.getTasksCount()
			internalTask.setTaskExecutor(taskExecutor)
			internalTask.setJobsCount(tasksCount.toLong())
			if (UiUtils.JADX_GUI_DEBUG) {
				LOG.debug(
					"Starting background task '{}', jobs count: {}, time limit: {} ms, memory check: {}",
					task.title,
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
		}
		try {
			val task = internalTask.getBgTask()
			internalTask.setJobsComplete(checkNotNull(internalTask.getTaskExecutor()).getProgress().toLong())
			internalTask.setStatus(TaskStatus.COMPLETE)
			internalTask.updateExecTime()
			task.onDone(internalTask)
		} catch (e: Exception) {
			LOG.error("Task complete failed", e)
			internalTask.setStatus(TaskStatus.ERROR)
		}
	}

	/** 在 EDT 上执行 `onFinish` 回调。 */
	private fun finishTask(internalTask: InternalTask) {
		try {
			internalTask.getBgTask().onFinish(internalTask)
		} catch (e: Exception) {
			LOG.error("Task onFinish failed", e)
			internalTask.setStatus(TaskStatus.ERROR)
		}
	}

	private fun removeTask(internalTask: InternalTask) {
		taskRunning.remove(internalTask.getId())
	}

	/** 取消单个任务：先请求取消，再在超时后强制关闭其内部线程池。 */
	private fun cancelTask(internalTask: InternalTask) {
		try {
			val task = internalTask.getBgTask()
			if (!internalTask.isRunning) {
				// 任务已完成或尚未开始
				task.cancel()
				internalTask.getJob()?.cancel()
				removeTask(internalTask)
				return
			}
			val taskExecutor = checkNotNull(internalTask.getTaskExecutor())
			// 请求强制终止
			task.cancel()
			taskExecutor.terminate()

			val executor = taskExecutor.getInternalExecutor() ?: return
			val cancelTimeout = task.cancelTimeoutMS
			if (cancelTimeout != 0) {
				if (executor.awaitTermination(cancelTimeout.toLong(), TimeUnit.MILLISECONDS)) {
					LOG.debug("Task cancel complete")
					return
				}
			}
			LOG.debug("Forcing tasks cancel")
			executor.shutdownNow()
			val complete = executor.awaitTermination(task.shutdownTimeoutMS.toLong(), TimeUnit.MILLISECONDS)
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
	private fun buildCancelCheck(internalTask: InternalTask, startTime: Long): () -> TaskStatus? {
		val task = internalTask.getBgTask()
		val timeLimit = task.timeLimit()
		val waitUntilTime = if (timeLimit == 0) 0L else startTime + timeLimit
		val checkMemoryUsage = task.checkMemoryUsage()
		return {
			if (task.isCanceled || Thread.currentThread().isInterrupted) {
				TaskStatus.CANCEL_BY_USER
			} else if (waitUntilTime != 0L && waitUntilTime < System.currentTimeMillis()) {
				LOG.warn("Task '{}' execution timeout, force cancel", task.title)
				TaskStatus.CANCEL_BY_TIMEOUT
			} else if (checkMemoryUsage && !UiUtils.isFreeMemoryAvailable) {
				LOG.warn("High memory usage: {}", UiUtils.memoryInfo())
				if (checkNotNull(internalTask.getTaskExecutor()).getThreadsCount() == 1) {
					LOG.warn("Task '{}' memory limit reached, force cancel", task.title)
					TaskStatus.CANCEL_BY_MEMORY
				} else {
					LOG.warn("Low free memory, reduce processing threads count to 1")
					checkNotNull(internalTask.getTaskExecutor()).setThreadsCount(1)
					System.gc()
					UiUtils.sleep(1000) // 等待 GC
					if (!UiUtils.isFreeMemoryAvailable) {
						LOG.error("Task '{}' memory limit reached (after GC), force cancel", task.title)
						TaskStatus.CANCEL_BY_MEMORY
					} else {
						null
					}
				}
			} else {
				null
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(BackgroundExecutor::class.java)

		/** [cancelAll] 等待运行中任务结束的最长时间（毫秒）。 */
		private const val CANCEL_WAIT_MS = 3000L
	}
}
