package jadx.gui.jobs

import jadx.api.utils.tasks.ITaskExecutor
import jadx.core.utils.tasks.TaskExecutor
import java.util.function.Consumer

/**
 * 简单任务：不可取消、带内存检查。
 *
 * **做什么**：把一组 [Runnable] 作为并行 job 交给 [TaskExecutor] 执行，
 * 完成后可在 EDT 上触发 [onFinish] 回调。常用于“加载代码”等一次性操作。
 *
 * **构造重载**（与原 Java 一一对应）：
 * - `(title, run)`：单个 job；
 * - `(title, run, onFinish)`：单个 job + 忽略状态的完成回调；
 * - `(title, jobs)`：多 job；
 * - `(title, jobs, onFinish)`：多 job + 带状态回调。
 *
 * **为什么不用 `data class`**：任务对象需要按引用比较。
 */
class SimpleTask(
	private val title: String,
	private val jobs: List<Runnable>,
	private val onFinish: Consumer<TaskStatus>?,
) : IBackgroundTask {

	constructor(title: String, run: Runnable) : this(title, listOf(run), null)

	constructor(title: String, run: Runnable, onFinish: Runnable) :
		this(title, listOf(run), Consumer<TaskStatus> { onFinish.run() })

	constructor(title: String, jobs: List<Runnable>) : this(title, jobs, null)

	override fun getTitle(): String = title

	fun getJobs(): List<Runnable> = jobs

	fun getOnFinish(): Consumer<TaskStatus>? = onFinish

	override fun scheduleTasks(): ITaskExecutor {
		val executor = TaskExecutor()
		executor.addParallelTasks(jobs)
		return executor
	}

	override fun onFinish(taskInfo: ITaskInfo) {
		onFinish?.accept(taskInfo.getStatus())
	}

	override fun checkMemoryUsage(): Boolean = true

	override fun canBeCanceled(): Boolean = false

	override fun isCanceled(): Boolean = false

	override fun cancel() {
	}
}
