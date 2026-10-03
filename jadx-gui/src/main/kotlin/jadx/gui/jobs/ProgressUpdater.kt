package jadx.gui.jobs

import jadx.gui.ui.panel.ProgressPanel
import jadx.gui.utils.NLS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 后台任务的进度刷新器。
 *
 * **做什么**：在 [scope]（[kotlinx.coroutines.Dispatchers.Swing]，即 EDT）上运行一个
 * 定时协程，按固定间隔（[UPDATE_INTERVAL_MS]）取出当前运行中的 [InternalTask]，
 * 刷新 [ProgressPanel]，并周期性执行取消检查。当前进度同时以 [StateFlow] 暴露，
 * 替代原先的进度回调管线。
 *
 * **线程模型**：
 * - 刷新循环是 [scope] 上的协程（EDT），因此面板更新天然在 EDT 上；
 * - 取消回调 [cancelCallback] 也在 EDT 上调用。
 */
class ProgressUpdater(
	private val progressPane: ProgressPanel,
	private val scope: CoroutineScope,
	private val cancelCallback: (InternalTask) -> Unit,
) {

	/** 当前进度状态（供外部观察，替代回调）。 */
	private val _progress = MutableStateFlow<ProgressState>(ProgressState.Hidden)
	val progress: StateFlow<ProgressState> = _progress.asStateFlow()

	/** 当前正在刷新进度的任务；由任务执行协程写入、刷新协程读取。 */
	private val currentTask = AtomicReference<InternalTask?>(null)

	init {
		scope.launch {
			while (isActive) {
				delay(UPDATE_INTERVAL_MS)
				tick()
			}
		}
	}

	/** 添加任务到刷新队列；静默任务不显示进度，直接跳过。 */
	fun addTask(task: InternalTask) {
		if (task.getBgTask().isSilent) {
			return
		}
		currentTask.set(task)
	}

	/** 任务完成：立即刷新一次（使进度面板复位/隐藏）。 */
	fun taskComplete(task: InternalTask) {
		if (currentTask.compareAndSet(task, null)) {
			applyState(ProgressState.Hidden)
		}
	}

	/** 单个刷新周期：更新进度、执行取消检查。 */
	private suspend fun tick() {
		val task = currentTask.get() ?: return
		if (!task.isRunning) {
			return
		}
		// 取消检查（含内存检测/GC）可能阻塞，放到 IO 线程执行，避免冻结 EDT
		val cancelStatus = withContext(Dispatchers.IO) { task.getCancelCheck().invoke() }
		if (cancelStatus != null) {
			task.setStatus(cancelStatus)
			applyState(ProgressState.Canceling(task.getBgTask().title))
			cancelCallback(task)
			return
		}
		val bgTask = task.getBgTask()
		val customProgress = bgTask.taskProgress
		val taskProgress = customProgress ?: TaskProgress(
			checkNotNull(task.getTaskExecutor()).getProgress().toLong(),
			task.jobsCount,
		)
		applyState(
			ProgressState.Active(
				title = bgTask.title,
				taskProgress = taskProgress,
				cancelable = bgTask.canBeCanceled(),
				firstUpdate = task.checkForFirstUpdate(),
			),
		)
	}

	/** 把进度状态应用到面板并发布到 [progress]。 */
	private fun applyState(state: ProgressState) {
		_progress.value = state
		when (state) {
			is ProgressState.Hidden -> {
				progressPane.reset()
				progressPane.setVisible(false)
			}

			is ProgressState.Active -> {
				if (state.firstUpdate) {
					progressPane.setLabel(state.title + "… ")
					progressPane.setCancelButtonVisible(state.cancelable)
					progressPane.setVisible(true)
				}
				progressPane.setProgress(state.taskProgress)
			}

			is ProgressState.Canceling -> {
				progressPane.setLabel(state.title + " (" + NLS.str("progress.canceling") + ")… ")
				progressPane.setCancelButtonVisible(false)
				progressPane.setIndeterminate(true)
			}
		}
	}
}

/** 进度面板状态。 */
sealed interface ProgressState {
	/** 无任务：面板隐藏并复位。 */
	data object Hidden : ProgressState

	/** 任务运行中。 */
	data class Active(
		val title: String,
		val taskProgress: ITaskProgress,
		val cancelable: Boolean,
		val firstUpdate: Boolean,
	) : ProgressState

	/** 任务正在取消。 */
	data class Canceling(val title: String) : ProgressState
}

/** 进度刷新间隔（毫秒）。 */
private const val UPDATE_INTERVAL_MS = 1000L
