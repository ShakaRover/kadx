package jadx.gui.jobs

import jadx.api.utils.tasks.ITaskExecutor
import jadx.core.utils.tasks.TaskExecutor
import jadx.gui.utils.NLS
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * 加载任务：先在后台线程准备数据，再回到 EDT 使用该数据。
 *
 * **做什么**：
 * - 后台 job 执行 [Supplier] 并把结果写入 [AtomicReference]；
 * - [onFinish]（EDT 上）再把该数据交给 [Consumer] 用于更新 UI。
 *
 * **为什么用 [AtomicReference]**：后台线程写入、EDT 读取，需要保证跨线程可见性。
 *
 * **为什么不用 `data class`**：任务对象需要按引用比较。
 */
class LoadTask<T> : CancelableBackgroundTask {

	private val title: String
	private val taskData: AtomicReference<T>
	private val bgTask: Runnable
	private val uiTask: Runnable

	constructor(loadBgTask: Supplier<T>, uiTask: Consumer<T>) :
		this(NLS.str("progress.load"), loadBgTask, uiTask)

	constructor(title: String, loadBgTask: Supplier<T>, uiTask: Consumer<T>) : super() {
		this.title = title
		this.taskData = AtomicReference()
		this.bgTask = Runnable { taskData.set(loadBgTask.get()) }
		this.uiTask = Runnable { uiTask.accept(taskData.get()) }
	}

	override fun getTitle(): String = title

	override fun scheduleTasks(): ITaskExecutor {
		val executor = TaskExecutor()
		executor.addSequentialTask(bgTask)
		return executor
	}

	override fun onFinish(taskInfo: ITaskInfo) {
		uiTask.run()
	}
}
