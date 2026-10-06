package kadx.core.utils.tasks

import kadx.api.KadxArgs
import kadx.api.utils.tasks.ITaskExecutor
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
 * **并发模型（N2a + P3 修正）**：调度协程仍跑在 [CoroutineScope] + [Dispatchers.Default] 上，
 * 但**并行阶段的并发度不再用 [Dispatchers.Default] + Semaphore 限流**：
 * `Dispatchers.Default` 的并行度等于 `Runtime.availableProcessors()`，会让 `-j` 大于核数时
 * **静默失效**（实测 10 核机器上 `-j 20` 只跑 10 个 worker，而上游是真开 20 条线程）。
 * 现在每个并行阶段临时建一个**定容固定线程池**（大小 = `min(任务数, threadsCount)`，
 * 线程名 `task-p`，与上游一致），阶段结束立即关闭；任务通过有界 [Channel] 投递，
 * 避免一次性物化十万量级协程。对外 API 保持同步形态，现有 CLI / GUI / 测试调用方无需改动。
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
	private val threadsCount = AtomicInteger(KadxArgs.DEFAULT_THREADS_COUNT)
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

	/**
	 * 并行执行一个阶段。
	 *
	 * **为什么用独立定容线程池**：`Dispatchers.Default` 的并行度等于
	 * `Runtime.availableProcessors()`，所以 `-j` 大于核数时会被**静默截断**
	 * （10 核机器上 `-j 20` 实测只跑 10 个 worker）。上游是
	 * `Executors.newFixedThreadPool(min(tasks, -j))`，真的开 `-j` 条线程；这里恢复同样语义。
	 *
	 * **为什么用 Channel 而不是一次性 async 全部任务**：阶段内任务数可达十万量级，
	 * `tasks.map { async { ... } }` 会一次性物化十万个协程对象。改为 `threads` 个 worker
	 * 从有界 Channel 取任务，并发度恰好是 `threads`，内存不随任务数增长。
	 *
	 * **结构化并发 / 取消语义**：`coroutineScope` 等所有 worker 结束；
	 * `terminateWithError` 取消 `executionJob` 会连带取消 worker。
	 * 注意 worker **不提前 break**：因为 [wrapTask] 对 terminating 幂等，
	 * 而若 worker 提前退出、生产者又正阻塞在 `send`，会死锁。
	 */
	private suspend fun runParallelStage(tasks: List<Runnable>, threads: Int) {
		val pool = Executors.newFixedThreadPool(threads, Utils.simpleThreadFactory("task-p"))
		val dispatcher = pool.asCoroutineDispatcher()
		try {
			val channel = Channel<Runnable>(capacity = threads * TASKS_CHANNEL_FACTOR)
			coroutineScope {
				repeat(threads) {
					launch(dispatcher) {
						for (task in channel) {
							wrapTask(task)
						}
					}
				}
				for (task in tasks) {
					if (terminating.get()) {
						break
					}
					channel.send(task)
				}
				channel.close()
			}
		} finally {
			dispatcher.close()
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

		/** 并行阶段任务队列的容量倍数：capacity = threads × 此值。 */
		private const val TASKS_CHANNEL_FACTOR = 4

		/** 阻塞等待线程池结束；超时（10 天）则抛出 [KadxRuntimeException]。 */
		fun awaitExecutorTermination(executor: ExecutorService) {
			try {
				val complete = executor.awaitTermination(10, TimeUnit.DAYS)
				if (!complete) {
					throw KadxRuntimeException("Executor timeout")
				}
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
			}
		}
	}
}
