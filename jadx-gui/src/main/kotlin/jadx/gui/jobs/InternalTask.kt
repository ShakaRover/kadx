package jadx.gui.jobs

import jadx.api.utils.tasks.ITaskExecutor
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 后台任务的运行时包装对象。
 *
 * **做什么**：把用户提供的 [IBackgroundTask] 与运行期状态（编号、起止时间、进度、
 * 状态、取消检查函数、底层执行器、协程 [Job]）绑定在一起，并作为 [ITaskInfo]
 * 暴露给任务结束回调。
 *
 * **并发说明**：`running` / `firstUpdate` 使用原子类型，因为进度刷新协程与任务
 * 执行协程会并发读写。
 *
 * **为什么不用 `data class`**：任务对象必须按引用比较（`===`）。
 */
class InternalTask(
	private val id: Long,
	private val bgTask: IBackgroundTask,
) : ITaskInfo {

	/** 是否正在运行。 */
	private val running = AtomicBoolean(false)

	/** 是否为第一次刷新（用于初始化进度面板）。 */
	private val firstUpdate = AtomicBoolean(true)

	/** 调度该任务的协程句柄，用于取消尚未开始的任务。 */
	@Volatile
	private var job: Job? = null

	private var startTime: Long = 0
	private var execTimeValue: Long = 0

	/** 取消检查函数：返回非 null 表示应取消并给出原因；未开始时为 null。 */
	private var cancelCheck: (() -> TaskStatus?)? = null

	private var statusValue: TaskStatus = TaskStatus.WAIT
	private var taskExecutor: ITaskExecutor? = null
	private var jobsCountValue: Long = 0
	private var jobsCompleteValue: Long = 0

	/** 记录任务开始：保存起始时间与取消检查函数，并置为运行中。 */
	fun taskStart(startTime: Long, cancelCheck: () -> TaskStatus?) {
		this.startTime = startTime
		this.cancelCheck = cancelCheck
		this.statusValue = TaskStatus.STARTED
		this.running.set(true)
	}

	/** 记录任务结束：停止运行标志，并把 STARTED 收敛为 COMPLETE。 */
	fun taskComplete() {
		this.running.set(false)
		if (statusValue == TaskStatus.STARTED) {
			// 也可能已被设置为 ERROR 或取消状态，此时不覆盖
			this.statusValue = TaskStatus.COMPLETE
		}
		updateExecTime()
	}

	fun getId(): Long = id

	fun getBgTask(): IBackgroundTask = bgTask

	fun setJob(job: Job) {
		this.job = job
	}

	fun getJob(): Job? = job

	val isRunning: Boolean get() = running.get()

	/** 原子地把“首次刷新”标志从 true 翻转为 false；仅第一次返回 true。 */
	fun checkForFirstUpdate(): Boolean = firstUpdate.compareAndExchange(true, false)

	/** 取消检查函数（调用前任务必然已启动，故此处必非空）。 */
	fun getCancelCheck(): () -> TaskStatus? = checkNotNull(cancelCheck)

	fun getStartTime(): Long = startTime

	override val status: TaskStatus get() = statusValue

	fun setStatus(taskStatus: TaskStatus) {
		this.statusValue = taskStatus
	}

	fun getTaskExecutor(): ITaskExecutor? = taskExecutor

	fun setTaskExecutor(taskExecutor: ITaskExecutor) {
		this.taskExecutor = taskExecutor
	}

	override val jobsComplete: Long get() = jobsCompleteValue

	fun setJobsComplete(jobsComplete: Long) {
		this.jobsCompleteValue = jobsComplete
	}

	override val jobsCount: Long get() = jobsCountValue

	fun setJobsCount(jobsCount: Long) {
		this.jobsCountValue = jobsCount
	}

	override val jobsSkipped: Long get() = jobsCountValue - jobsCompleteValue

	override val time: Long get() = execTimeValue

	/** 根据当前时间刷新执行耗时。 */
	fun updateExecTime() {
		this.execTimeValue = System.currentTimeMillis() - startTime
	}

	override fun toString(): String = "InternalTask{" + bgTask.title + ", status=" + statusValue +
		", progress=" + jobsCompleteValue + " of " + jobsCountValue + '}'
}
