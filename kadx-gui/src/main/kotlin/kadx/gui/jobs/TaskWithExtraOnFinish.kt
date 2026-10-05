package kadx.gui.jobs

import kadx.api.utils.tasks.ITaskExecutor
import kotlinx.coroutines.flow.Flow

/**
 * 给已有任务追加一个 `onFinish` 动作的包装器。
 *
 * **做什么**：把 [IBackgroundTask] 的调用全部转发给被包装的任务，
 * 但 [onFinish] 会先调用原任务的回调，再执行额外的 [extraOnFinish]。
 * 典型用途：代码加载完成后再刷新编辑器内容或树节点。
 *
 * **为什么不用 `data class`**：任务对象需要按引用比较。
 */
class TaskWithExtraOnFinish : IBackgroundTask {

	private val task: IBackgroundTask
	private val extraOnFinish: (TaskStatus) -> Unit

	constructor(task: IBackgroundTask, extraOnFinish: Runnable) :
		this(task, { extraOnFinish.run() })

	constructor(task: IBackgroundTask, extraOnFinish: (TaskStatus) -> Unit) {
		this.task = requireNotNull(task)
		this.extraOnFinish = requireNotNull(extraOnFinish)
	}

	override fun onFinish(taskInfo: ITaskInfo) {
		task.onFinish(taskInfo)
		extraOnFinish(taskInfo.status)
	}

	override val title: String get() = task.title

	override fun scheduleTasks(): ITaskExecutor = task.scheduleTasks()

	override fun onDone(taskInfo: ITaskInfo) {
		task.onDone(taskInfo)
	}

	override val progressFlow: Flow<ITaskProgress> get() = task.progressFlow

	override val taskProgress: ITaskProgress? get() = task.taskProgress

	override fun canBeCanceled(): Boolean = task.canBeCanceled()

	override val isCanceled: Boolean get() = task.isCanceled

	override fun cancel() {
		task.cancel()
	}

	override fun timeLimit(): Int = task.timeLimit()

	override fun checkMemoryUsage(): Boolean = task.checkMemoryUsage()

	override val cancelTimeoutMS: Int get() = task.cancelTimeoutMS

	override val shutdownTimeoutMS: Int get() = task.shutdownTimeoutMS
}
