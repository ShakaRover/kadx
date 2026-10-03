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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * 搜索后台任务。
 *
 * **做什么**：把若干 [ISearchProvider] 包装成 [SearchJob]，交给
 * [BackgroundExecutor] 在后台线程执行，并通过回调把结果推给 UI。
 *
 * **线程模型（N1）**：
 * - [fetchResults] / [addResult] / [waitTask] 用 `@Synchronized` 保护共享状态；
 * - 实际搜索在 [BackgroundExecutor] 的协程上执行（耗时部分在 `Dispatchers.IO`）；
 * - 进度通过 [getProgressFlow] 发布，结果回调 [resultsListener] 由调用方保证在 EDT 上消费。
 */
class SearchTask(
	mainWindow: MainWindow,
	private val resultsListener: (JNode) -> Unit,
	private val onFinishCallback: (ITaskInfo, Boolean) -> Unit,
) : CancelableBackgroundTask() {

	private val backgroundExecutor: BackgroundExecutor = mainWindow.getBackgroundExecutor()
	private val jobs: MutableList<SearchJob> = ArrayList()
	private val taskProgress = TaskProgress()

	private val resultsCount = AtomicInteger(0)
	private var resultsLimit = 0
	private var deferred: Deferred<TaskStatus>? = null

	private val progressFlow = MutableSharedFlow<ITaskProgress>(replay = 1)

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
		if (deferred != null) {
			throw IllegalStateException("Previous task not yet finished")
		}
		resetCancel()
		resultsCount.set(0)
		taskProgress.updateTotal(jobs.sumOf { it.getProvider().total() })
		deferred = backgroundExecutor.executeAsync(this)
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
		resultsListener(resultNode)
		if (resultsLimit != 0 && resultsCount.incrementAndGet() >= resultsLimit) {
			cancel()
			return true
		}
		return false
	}

	/** 等待当前搜索任务结束（最多 200ms），并清空 deferred。 */
	@Synchronized
	fun waitTask() {
		val currentDeferred = deferred ?: return
		try {
			runBlocking { withTimeoutOrNull(200) { currentDeferred.await() } }
		} catch (e: Exception) {
			LOG.warn("Search task wait error", e)
			currentDeferred.cancel()
		} finally {
			deferred = null
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
		onFinishCallback(taskInfo, complete)
	}

	override fun checkMemoryUsage(): Boolean = true

	override fun getTaskProgress(): ITaskProgress {
		taskProgress.updateProgress(jobs.sumOf { it.getProvider().progress() })
		progressFlow.tryEmit(taskProgress)
		return taskProgress
	}

	override fun getProgressFlow(): Flow<ITaskProgress> = progressFlow.asSharedFlow()

	override fun getCancelTimeoutMS(): Int = 0

	override fun getShutdownTimeoutMS(): Int = 10

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SearchTask::class.java)
	}
}
