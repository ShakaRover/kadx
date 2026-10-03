package jadx.gui.jobs

import jadx.api.utils.tasks.ITaskExecutor
import jadx.core.utils.tasks.TaskExecutor

/**
 * 静默任务：简单且耗时短，不显示进度条。
 *
 * **做什么**：把单个 [Runnable] 作为串行 job 交给 [TaskExecutor] 执行；
 * [isSilent] 返回 true，因此 [ProgressUpdater] 会跳过它。
 *
 * **为什么不用 `data class`**：任务对象需要按引用比较。
 */
class SilentTask(
	private val task: Runnable,
) : CancelableBackgroundTask() {

	override fun isSilent(): Boolean = true

	override fun getTitle(): String = "<silent>"

	override fun scheduleTasks(): ITaskExecutor {
		val executor = TaskExecutor()
		executor.addSequentialTask(task)
		return executor
	}
}
