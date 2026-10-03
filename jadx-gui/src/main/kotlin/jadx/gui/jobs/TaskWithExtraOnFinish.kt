package jadx.gui.jobs

import jadx.api.utils.tasks.ITaskExecutor
import kotlinx.coroutines.flow.Flow
import java.util.function.Consumer

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
	private val extraOnFinish: Consumer<TaskStatus>

	constructor(task: IBackgroundTask, extraOnFinish: Runnable) :
		this(task, Consumer<TaskStatus> { extraOnFinish.run() })

	constructor(task: IBackgroundTask, extraOnFinish: Consumer<TaskStatus>) {
		this.task = requireNotNull(task)
		this.extraOnFinish = requireNotNull(extraOnFinish)
	}

	override fun onFinish(taskInfo: ITaskInfo) {
		task.onFinish(taskInfo)
		extraOnFinish.accept(taskInfo.getStatus())
	}

	override fun getTitle(): String = task.getTitle()

	override fun scheduleTasks(): ITaskExecutor = task.scheduleTasks()

	override fun onDone(taskInfo: ITaskInfo) {
		task.onDone(taskInfo)
	}

	override fun getProgressFlow(): Flow<ITaskProgress> = task.getProgressFlow()

	override fun getTaskProgress(): ITaskProgress? = task.getTaskProgress()

	override fun canBeCanceled(): Boolean = task.canBeCanceled()

	override fun isCanceled(): Boolean = task.isCanceled()

	override fun cancel() {
		task.cancel()
	}

	override fun timeLimit(): Int = task.timeLimit()

	override fun checkMemoryUsage(): Boolean = task.checkMemoryUsage()

	override fun getCancelTimeoutMS(): Int = task.getCancelTimeoutMS()

	override fun getShutdownTimeoutMS(): Int = task.getShutdownTimeoutMS()
}
