package jadx.gui.search

import jadx.api.utils.tasks.ITaskExecutor
import jadx.core.utils.tasks.TaskExecutor
import jadx.gui.jobs.BackgroundExecutor
import jadx.gui.jobs.CancelableBackgroundTask
import jadx.gui.jobs.ITaskInfo
import jadx.gui.jobs.ITaskProgress
import jadx.gui.jobs.TaskProgress
import jadx.gui.jobs.TaskStatus
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * 搜索后台任务。
 *
 * **做什么**：把若干 [ISearchProvider] 包装成 [SearchJob]，交给
 * [BackgroundExecutor] 在后台线程执行，并通过回调把结果推给 UI。
 *
 * **线程模型**（阶段 5.1 保持原 Swing 模型，不引入协程）：
 * - [fetchResults] / [addResult] / [waitTask] 用 `@Synchronized` 保护共享状态；
 * - 实际搜索跑在 [BackgroundExecutor] 的线程池里；
 * - 结果回调 [resultsListener] 由调用方保证在 EDT 上消费。
 */
class SearchTask(
	mainWindow: MainWindow,
	private val resultsListener: Consumer<JNode>,
	private val onFinishCallback: BiConsumer<ITaskInfo, Boolean>,
) : CancelableBackgroundTask() {

	private val backgroundExecutor: BackgroundExecutor = mainWindow.getBackgroundExecutor()
	private val jobs: MutableList<SearchJob> = ArrayList()
	private val taskProgress = TaskProgress()

	private val resultsCount = AtomicInteger(0)
	private var resultsLimit = 0
	private var future: Future<TaskStatus>? = null

	private var progressListener: Consumer<ITaskProgress>? = null

	/** 注册一个搜索提供者。 */
	fun addProviderJob(provider: ISearchProvider) {
		jobs.add(SearchJob(this, provider))
	}

	fun setResultsLimit(limit: Int) {
		this.resultsLimit = limit
	}

	/** 提交并开始执行本次搜索（同一实例上一次任务未结束时会抛异常）。 */
	@Synchronized
	fun fetchResults() {
		if (future != null) {
			throw IllegalStateException("Previous task not yet finished")
		}
		resetCancel()
		resultsCount.set(0)
		taskProgress.updateTotal(jobs.stream().mapToInt { it.getProvider().total() }.sum())
		future = backgroundExecutor.executeWithFuture(this)
	}

	/**
	 * 接收一个搜索结果。
	 *
	 * @return `true` 表示应停止搜索（已取消或达到结果上限）
	 */
	@Synchronized
	fun addResult(resultNode: JNode): Boolean {
		if (isCanceled()) {
			// 取消后忽略新结果
			return true
		}
		resultsListener.accept(resultNode)
		if (resultsLimit != 0 && resultsCount.incrementAndGet() >= resultsLimit) {
			cancel()
			return true
		}
		return false
	}

	/** 等待当前搜索任务结束（最多 200ms），并清空 future。 */
	@Synchronized
	fun waitTask() {
		val currentFuture = future ?: return
		try {
			currentFuture.get(200, TimeUnit.MILLISECONDS)
		} catch (e: Exception) {
			LOG.warn("Search task wait error", e)
			currentFuture.cancel(true)
		} finally {
			future = null
		}
	}

	override fun getTitle(): String = NLS.str("search_dialog.tip_searching")

	override fun scheduleTasks(): ITaskExecutor {
		val executor = TaskExecutor()
		executor.addParallelTasks(jobs)
		return executor
	}

	override fun onFinish(taskInfo: ITaskInfo) {
		val complete = !isCanceled() &&
			taskInfo.getStatus() == TaskStatus.COMPLETE &&
			taskInfo.getJobsComplete() == taskInfo.getJobsCount()
		onFinishCallback.accept(taskInfo, complete)
	}

	override fun checkMemoryUsage(): Boolean = true

	override fun getTaskProgress(): ITaskProgress {
		taskProgress.updateProgress(jobs.stream().mapToInt { it.getProvider().progress() }.sum())
		return taskProgress
	}

	fun setProgressListener(progressListener: Consumer<ITaskProgress>?) {
		this.progressListener = progressListener
	}

	override fun getProgressListener(): Consumer<ITaskProgress>? = progressListener

	override fun getCancelTimeoutMS(): Int = 0

	override fun getShutdownTimeoutMS(): Int = 10

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SearchTask::class.java)
	}
}
