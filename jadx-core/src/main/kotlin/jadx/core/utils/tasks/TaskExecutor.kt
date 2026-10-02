package jadx.core.utils.tasks

import jadx.api.JadxArgs
import jadx.api.utils.tasks.ITaskExecutor
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 分阶段任务执行器（[ITaskExecutor] 的默认实现）。
 *
 * **模型**：任务按“阶段（stage）”组织，每个阶段要么并行执行（线程池大小受 [threadsCount] 限制），
 * 要么串行执行；所有阶段在单个调度线程上依次推进。等价于一个简化的 fork-join 流程。
 *
 * **生命周期**：`execute()` 启动后台调度线程；`terminate()` 请求停止；`awaitTermination()` 阻塞等待完成。
 *
 * **Kotlin 转换说明**：
 * - 覆写 Java 接口 [ITaskExecutor] 的方法保持原签名（`List<? extends Runnable>` → Kotlin `List<Runnable>`，
 *   依赖 Kotlin `List` 的声明式协变）；
 * - `awaitExecutorTermination` 原为静态方法，放入 `companion object` + `@JvmStatic`，Java 调用不变；
 * - 内部共享状态使用 [AtomicInteger]/[AtomicBoolean]，与 Java 版一致；
 * - `executor` 字段在 Kotlin 中如实声明为可空，访问处用 [checkNotNull] 还原原 Java 的“此处必非空”假设。
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
	private var executor: ExecutorService? = null
	private var tasksCount = 0
	private var terminateError: Error? = null

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
			if (running.get() || executor != null) {
				throw IllegalStateException("Already executing")
			}
			executor = Executors.newFixedThreadPool(1, Utils.simpleThreadFactory("task-s"))
			running.set(true)
			terminating.set(false)
			progress.set(0)
			checkNotNull(executor).execute { runStages() }
		}
	}

	private fun stopExecution() {
		synchronized(executorSync) {
			running.set(false)
			terminating.set(true)
			executor?.shutdown()
			executor = null
		}
	}

	override fun awaitTermination() {
		val activeExecutor = executor
		if (activeExecutor != null && running.get()) {
			awaitExecutorTermination(activeExecutor)
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
		checkNotNull(executor).shutdownNow()
	}

	override fun isTerminating(): Boolean = terminating.get()

	override fun isRunning(): Boolean = running.get()

	override fun getInternalExecutor(): ExecutorService? = executor

	private fun runStages() {
		try {
			for (stage in stages) {
				val threads = Math.min(stage.tasks.size, threadsCount.get())
				if (stage.type == ExecType.SEQUENTIAL || threads == 1) {
					for (task in stage.tasks) {
						wrapTask(task)
					}
				} else {
					val parallelExecutor = Executors.newFixedThreadPool(
						threads,
						Utils.simpleThreadFactory("task-p"),
					)
					for (task in stage.tasks) {
						parallelExecutor.execute { wrapTask(task) }
					}
					parallelExecutor.shutdown()
					awaitExecutorTermination(parallelExecutor)
				}
				if (terminating.get()) {
					break
				}
			}
		} finally {
			stopExecution()
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
