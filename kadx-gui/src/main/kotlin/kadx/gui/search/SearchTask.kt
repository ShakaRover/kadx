package kadx.gui.search

import kadx.api.utils.tasks.ITaskExecutor
import kadx.core.utils.tasks.TaskExecutor
import kadx.gui.jobs.BackgroundExecutor
import kadx.gui.jobs.CancelableBackgroundTask
import kadx.gui.jobs.ITaskInfo
import kadx.gui.jobs.ITaskProgress
import kadx.gui.jobs.TaskProgress
import kadx.gui.jobs.TaskStatus
import kadx.gui.treemodel.JNode
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
	private val taskProgressState = TaskProgress()

	/** 结果数量策略：分页上限 + [MAX_RESULTS_LIMIT] 硬上限。 */
	private val resultsLimiter = SearchResultsLimiter()

	private var resultsLimit = 0
	private var deferred: Deferred<TaskStatus>? = null

	private val progressFlowState = MutableSharedFlow<ITaskProgress>(replay = 1)

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
		resultsLimiter.resetPage()
		taskProgressState.updateTotal(jobs.sumOf { it.getProvider().total() })
		deferred = backgroundExecutor.executeAsync(this)
	}

	/**
	 * 接收一个搜索结果。
	 *
	 * @return `true` 表示应停止搜索（已取消、已暂停或达到结果上限）
	 */
	@Synchronized
	fun addResult(resultNode: JNode): Boolean {
		if (isCanceled) {
			// 取消/暂停后忽略新结果
			return true
		}
		resultsListener(resultNode)
		if (resultsLimiter.onResult(resultsLimit)) {
			pause()
			return true
		}
		return false
	}

	/**
	 * 达到结果上限时**暂停**搜索（而非用户主动取消）。
	 *
	 * provider 只通过 [Cancelable.isCanceled] 观察停止信号（[ISearchProvider.next] 的入参），
	 * 所以这里必须置位取消标志；区别在于**原因被单独记录**（[isHardLimitReached]），
	 * 且 [fetchResults] 会清除标志，因此“加载更多”能从 provider 游标续跑而不是重跑。
	 */
	private fun pause() {
		cancel()
	}

	/** 是否因触达 [MAX_RESULTS_LIMIT] 硬上限而停止（应提示用户细化搜索）。 */
	val isHardLimitReached: Boolean get() = resultsLimiter.isHardLimitReached

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

	override val title: String get() = NLS.str("search_dialog.tip_searching")

	override fun scheduleTasks(): ITaskExecutor {
		val executor = TaskExecutor()
		executor.addParallelTasks(jobs)
		return executor
	}

	override fun onFinish(taskInfo: ITaskInfo) {
		val complete = !isCanceled &&
			taskInfo.status == TaskStatus.COMPLETE &&
			taskInfo.jobsComplete == taskInfo.jobsCount
		onFinishCallback(taskInfo, complete)
	}

	override fun checkMemoryUsage(): Boolean = true

	override val taskProgress: ITaskProgress
		get() {
			taskProgressState.updateProgress(jobs.sumOf { it.getProvider().progress() })
			progressFlowState.tryEmit(taskProgressState)
			return taskProgressState
		}

	override val progressFlow: Flow<ITaskProgress> get() = progressFlowState.asSharedFlow()

	override val cancelTimeoutMS: Int get() = 0

	override val shutdownTimeoutMS: Int get() = 10

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SearchTask::class.java)

		/**
		 * 单次搜索的结果数硬上限。
		 *
		 * “加载全部”会把 `resultsLimit` 设为 0（无限制），此时结果集（`ResultsModel.rows`
		 * 与随之增长的 `JNodeCache`）会无界增长。实测单个 `JNode` + 其关联 Java 节点
		 * 量级不小，50k 条已足够覆盖“真想全部看完”的场景，再多应引导用户细化搜索。
		 */
		const val MAX_RESULTS_LIMIT = 50_000
	}
}
