@file:Suppress("ktlint:standard:property-naming")

package kadx.gui.jobs

import kadx.api.ICodeCache
import kadx.api.JavaClass
import kadx.api.utils.tasks.ITaskExecutor
import kadx.commons.app.KadxCommonEnv
import kadx.core.utils.tasks.TaskExecutor
import kadx.gui.KadxWrapper
import kadx.gui.ui.MainWindow
import kadx.gui.utils.ExplicitGc
import kadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JOptionPane

/**
 * 反编译任务：把工程内所有类反编译并建立索引。
 *
 * **做什么**：从 [KadxWrapper] 取出待处理的类，按批次拆分为并行 job，逐个反编译
 * 并写入代码缓存；完成后统计被跳过的类数量并提示用户。
 *
 * **时间上限**：按类数量线性估算（[calcDecompileTimeLimit]），超过则被
 * [BackgroundExecutor] 强制取消（[TaskStatus.CANCEL_BY_TIMEOUT]）。
 *
 * **为什么不用 `data class`**：任务对象需要按引用比较。
 */
class DecompileTask(private val mainWindow: MainWindow) : CancelableBackgroundTask() {

	private val wrapper: KadxWrapper = mainWindow.getWrapper()

	/** 已完成（含跳过）的类计数。 */
	private val complete = AtomicInteger(0)

	/** 预期完成的类数量（[scheduleJobs] 时确定）。 */
	private var expectedCompleteCount: Int = 0

	private var result: ProcessResult? = null

	override val title: String get() = NLS.str("progress.decompile")

	override fun scheduleTasks(): ITaskExecutor {
		val executor = TaskExecutor()
		executor.addParallelTasks(scheduleJobs())
		return executor
	}

	/** 构建反编译 job 列表；已完成全量反编译或批次构建失败时返回空列表。 */
	fun scheduleJobs(): List<Runnable> {
		if (mainWindow.getCacheObject().isFullDecompilationFinished) {
			return emptyList()
		}

		val classes = wrapper.includedClasses
		expectedCompleteCount = classes.size
		complete.set(0)

		val batches: List<List<JavaClass>>
		try {
			batches = wrapper.buildDecompileBatches(classes)
		} catch (e: Exception) {
			LOG.error("Decompile batches build error", e)
			return emptyList()
		}
		return getJobs(batches)
	}

	/** 为每个批次生成一个 job：逐类反编译（缓存已有则跳过），并累加完成计数。 */
	private fun getJobs(batches: List<List<JavaClass>>): List<Runnable> {
		val codeCache: ICodeCache = wrapper.args.codeCache
		val jobs = ArrayList<Runnable>(batches.size)
		for (batch in batches) {
			jobs.add {
				for (cls in batch) {
					if (isCanceled) {
						return@add
					}
					try {
						if (!codeCache.contains(cls.getRawName())) {
							cls.decompile()
						}
					} catch (e: Throwable) {
						LOG.error("Failed to decompile class: {}", cls, e)
					} finally {
						complete.incrementAndGet()
					}
				}
			}
		}
		return jobs
	}

	override fun onDone(taskInfo: ITaskInfo) {
		val taskTime = taskInfo.time
		val avgPerCls = taskTime / maxOf(expectedCompleteCount, 1)
		val timeLimit = timeLimit()
		val skippedCls = expectedCompleteCount - complete.get()
		if (LOG.isInfoEnabled) {
			LOG.info(
				"Decompile and index task complete in " + taskTime + " ms (avg " + avgPerCls + " ms per class)" +
					", classes: " + expectedCompleteCount +
					", skipped: " + skippedCls +
					", time limit:{ total: " + timeLimit + "ms, per cls: " + CLS_LIMIT + "ms }" +
					", status: " + taskInfo.status,
			)
		}
		result = ProcessResult(skippedCls, taskInfo.status, timeLimit)

		wrapper.unloadClasses()
		processDecompilationResults()
		ExplicitGc.run("after full decompilation: unloadClasses")

		mainWindow.getCacheObject().setFullDecompilationFinished(skippedCls == 0)
	}

	/** 根据跳过数量与结束状态，向用户提示未完成的原因。 */
	private fun processDecompilationResults() {
		val result = checkNotNull(this.result)
		val skippedCls = result.getSkipped()
		if (skippedCls == 0) {
			return
		}
		val status = result.getStatus()
		LOG.warn("Decompile and indexing of some classes skipped: {}, status: {}", skippedCls, status)
		when (status) {
			TaskStatus.CANCEL_BY_USER -> {
				val reason = NLS.str("message.userCancelTask")
				val message = NLS.str("message.indexIncomplete", reason, skippedCls)
				JOptionPane.showMessageDialog(mainWindow, message)
			}

			TaskStatus.CANCEL_BY_TIMEOUT -> {
				val reason = NLS.str("message.taskTimeout", result.getTimeLimit())
				val message = NLS.str("message.indexIncomplete", reason, skippedCls)
				JOptionPane.showMessageDialog(mainWindow, message)
			}

			TaskStatus.CANCEL_BY_MEMORY -> {
				mainWindow.showHeapUsageBar()
				JOptionPane.showMessageDialog(mainWindow, NLS.str("message.indexingClassesSkipped", skippedCls))
			}

			else -> {}
		}
	}

	override fun canBeCanceled(): Boolean = true

	override fun timeLimit(): Int = calcDecompileTimeLimit(expectedCompleteCount)

	override fun checkMemoryUsage(): Boolean = true

	fun getResult(): ProcessResult? = result

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DecompileTask::class.java)

		/** 每个类的处理时间预算（毫秒），可通过环境变量覆盖。 */
		private val CLS_LIMIT: Int = KadxCommonEnv.getInt("KADX_CLS_PROCESS_LIMIT", 50)

		/** 根据类数量估算反编译任务的时间上限（毫秒）。 */
		fun calcDecompileTimeLimit(classCount: Int): Int = classCount * CLS_LIMIT + 5000
	}
}
