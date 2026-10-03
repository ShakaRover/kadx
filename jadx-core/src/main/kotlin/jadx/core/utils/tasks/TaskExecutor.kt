package jadx.core.utils.tasks

import jadx.api.JadxArgs
import jadx.api.utils.tasks.ITaskExecutor
import jadx.core.utils.exceptions.JadxRuntimeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 分阶段任务执行器（[ITaskExecutor] 的默认实现）。
 *
 * **模型**：任务按“阶段（stage）”组织，每个阶段要么并行执行（并发度受 [threadsCount] 限制），
 * 要么串行执行；所有阶段在单个调度协程上依次推进。
 *
 * **协程化说明（N2a）**：调度线程由 `ExecutorService` 改为 [CoroutineScope] + [Dispatchers.Default]；
 * 并行阶段的并发度由固定线程池改为 [Semaphore] 限流，保持相同并发度；用 `async` + `awaitAll`
 * 结构化并发。对外 API 保持同步形态，现有 CLI / GUI / 测试调用方无需改动。
 */
class TaskExecutor : ITaskExecutor {

	private enum class ExecType {
		PARALLEL,
		SEQUENTIAL,
	}

	/** 一个执行阶段：类型 + 任务列表（任务列表本身只读，阶段之间串行推进）。 */
	private class ExecStage(
		val type: ExecType,
		val tasks: List<Runnable>,
	)

	private val stages: MutableList<ExecStage> = ArrayList()
	private val threadsCount = AtomicInteger(JadxArgs.DEFAULT_THREADS_COUNT)
	private val progress = AtomicInteger(0)
	private val running = AtomicBoolean(false)
	private val terminating = AtomicBoolean(false)
	private val executorSync = Any()

	/** 任务调度协程作用域（共享 [Dispatchers.Default]）。 */
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	/** 当前执行的调度协程。 */
	@Volatile
	private var executionJob: Job? = null

	/** 执行结束信号，供同步的 [awaitTermination] 阻塞等待。 */
	@Volatile
	private var finishLatch: CountDownLatch? = null

	/** [getInternalExecutor] 返回的兼容视图（执行期间非空）。 */
	@Volatile
	private var internalExecutor: ExecutorService? = null

	@Volatile
	private var terminateError: Error? = null

	private var tasksCount = 0

	override fun addParallelTasks(parallelTasks: List<Runnable>) {
		if (parallelTasks.isEmpty()) {
			return
		}
		tasksCount += parallelTasks.size
		stages.add(ExecStage(ExecType.PARALLEL, parallelTasks))
	}

	override fun addSequentialTasks(seqTasks: List<Runnable>) {
		if (seqTasks.isEmpty()) {
			return
		}
		tasksCount += seqTasks.size
		stages.add(ExecStage(ExecType.SEQUENTIAL, seqTasks))
	}

	override fun addSequentialTask(seqTask: Runnable) {
		addSequentialTasks(Collections.singletonList(seqTask))
	}

	override fun getThreadsCount(): Int = threadsCount.get()

	override fun setThreadsCount(count: Int) {
		threadsCount.set(count)
	}

	override fun getTasksCount(): Int = tasksCount

	override fun getProgress(): Int = progress.get()

	override fun execute() {
		synchronized(executorSync) {
			if (running.get() || executionJob != null) {
				throw IllegalStateException("Already executing")
			}
			running.set(true)
			terminating.set(false)
			progress.set(0)
			terminateError = null
			finishLatch = CountDownLatch(1)
			internalExecutor = CoroutineExecutorService()
			executionJob = scope.launch { runStages() }
		}
	}

	private fun stopExecution() {
		synchronized(executorSync) {
			running.set(false)
			executionJob = null
			internalExecutor = null
			finishLatch?.countDown()
		}
	}

	override fun awaitTermination() {
		val latch = finishLatch
		if (latch != null && running.get()) {
			try {
				latch.await()
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
			}
		}
		val error = terminateError
		if (error != null) {
			throw error
		}
	}

	override fun terminate() {
		terminating.set(true)
	}

	private fun terminateWithError(error: Error) {
		if (terminating.get()) {
			return
		}
		terminateError = error
		terminate()
		executionJob?.cancel()
	}

	override fun isTerminating(): Boolean = terminating.get()

	override fun isRunning(): Boolean = running.get()

	override fun getInternalExecutor(): ExecutorService? = internalExecutor

	private suspend fun runStages() {
		try {
			for (stage in stages) {
				if (terminating.get()) {
					break
				}
				val threads = Math.min(stage.tasks.size, threadsCount.get())
				if (stage.type == ExecType.SEQUENTIAL || threads <= 1) {
					for (task in stage.tasks) {
						if (terminating.get()) {
							break
						}
						wrapTask(task)
					}
				} else {
					runParallelStage(stage.tasks, threads)
				}
			}
		} finally {
			stopExecution()
		}
	}

	/** 并行执行一个阶段：并发度由 [Semaphore] 限制，阶段内任务全部完成后返回。 */
	private suspend fun runParallelStage(tasks: List<Runnable>, threads: Int) {
		val semaphore = Semaphore(threads)
		coroutineScope {
			tasks.map { task ->
				async {
					semaphore.withPermit { wrapTask(task) }
				}
			}.awaitAll()
		}
	}

	private fun wrapTask(task: Runnable) {
		if (terminating.get()) {
			return
		}
		try {
			task.run()
			progress.incrementAndGet()
		} catch (e: Error) {
			terminateWithError(e)
		} catch (e: Exception) {
			LOG.error("Unhandled task exception:", e)
		}
	}

	/**
	 * [ExecutorService] 兼容视图：把命令提交到协程作用域，取消映射为取消调度协程。
	 * 仅用于保留旧的 `getInternalExecutor()` 调用方（GUI 取消/超时）。
	 */
	private inner class CoroutineExecutorService : AbstractExecutorService() {
		@Volatile
		private var shutdown = false

		override fun execute(command: Runnable) {
			if (shutdown) {
				throw RejectedExecutionException("TaskExecutor is stopped")
			}
			scope.launch { command.run() }
		}

		override fun shutdown() {
			shutdown = true
		}

		override fun shutdownNow(): MutableList<Runnable> {
			shutdown = true
			executionJob?.cancel()
			return ArrayList()
		}

		override fun isShutdown(): Boolean = shutdown

		override fun isTerminated(): Boolean = finishLatch?.count == 0L

		override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
			val latch = finishLatch ?: return true
			return try {
				latch.await(timeout, unit)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				false
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TaskExecutor::class.java)

		/** 阻塞等待线程池结束；超时（10 天）则抛出 [JadxRuntimeException]。 */
		@JvmStatic
		fun awaitExecutorTermination(executor: ExecutorService) {
			try {
				val complete = executor.awaitTermination(10, TimeUnit.DAYS)
				if (!complete) {
					throw JadxRuntimeException("Executor timeout")
				}
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
			}
		}
	}
}
