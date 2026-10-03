package jadx.gui.jobs

import jadx.api.utils.tasks.ITaskExecutor
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

/**
 * 后台任务的运行时包装对象。
 *
 * **做什么**：把用户提供的 [IBackgroundTask] 与运行期状态（编号、起止时间、进度、
 * 状态、取消检查函数、底层执行器）绑定在一起。它同时实现：
 * - [Delayed]：用于放入 [java.util.concurrent.DelayQueue]，按“下次刷新时间”排序；
 * - [ITaskInfo]：作为任务结束回调中暴露给外部的只读信息。
 *
 * **并发说明**：`running` / `nextUpdate` / `firstUpdate` 使用原子类型，
 * 因为 [ProgressUpdater] 的刷新线程与任务执行线程会并发读写。
 *
 * **为什么不用 `data class`**：任务对象必须按引用比较（`===`）。
 */
class InternalTask(
	private val id: Long,
	private val bgTask: IBackgroundTask,
) : Delayed,
	ITaskInfo {

	/** 是否正在运行。 */
	private val running = AtomicBoolean(false)

	/** 下次进度刷新时间（毫秒时间戳）。 */
	private val nextUpdate = AtomicLong(0)

	/** 是否为第一次刷新（用于初始化进度面板）。 */
	private val firstUpdate = AtomicBoolean(true)

	private var startTime: Long = 0
	private var execTime: Long = 0

	/** 取消检查函数：返回非 null 表示应取消并给出原因；未开始时为 null。 */
	private var cancelCheck: Supplier<TaskStatus?>? = null

	private var status: TaskStatus = TaskStatus.WAIT
	private var taskExecutor: ITaskExecutor? = null
	private var jobsCount: Long = 0
	private var jobsComplete: Long = 0

	/** 记录任务开始：保存起始时间与取消检查函数，并置为运行中。 */
	fun taskStart(startTime: Long, cancelCheck: Supplier<TaskStatus?>) {
		this.startTime = startTime
		this.cancelCheck = cancelCheck
		this.status = TaskStatus.STARTED
		this.running.set(true)
	}

	/** 记录任务结束：停止运行标志，并把 STARTED 收敛为 COMPLETE。 */
	fun taskComplete() {
		this.running.set(false)
		if (status == TaskStatus.STARTED) {
			// 也可能已被设置为 ERROR 或取消状态，此时不覆盖
			this.status = TaskStatus.COMPLETE
		}
		updateExecTime()
	}

	fun getId(): Long = id

	fun getBgTask(): IBackgroundTask = bgTask

	fun setNextUpdate(nextUpdate: Long) {
		this.nextUpdate.set(nextUpdate)
	}

	fun isRunning(): Boolean = running.get()

	/** 原子地把“首次刷新”标志从 true 翻转为 false；仅第一次返回 true。 */
	fun checkForFirstUpdate(): Boolean = firstUpdate.compareAndExchange(true, false)

	/** 取消检查函数（调用前任务必然已启动，故此处必非空）。 */
	fun getCancelCheck(): Supplier<TaskStatus?> = checkNotNull(cancelCheck)

	fun getStartTime(): Long = startTime

	override fun getStatus(): TaskStatus = status

	fun setStatus(taskStatus: TaskStatus) {
		this.status = taskStatus
	}

	fun getTaskExecutor(): ITaskExecutor? = taskExecutor

	fun setTaskExecutor(taskExecutor: ITaskExecutor) {
		this.taskExecutor = taskExecutor
	}

	override fun getJobsComplete(): Long = jobsComplete

	fun setJobsComplete(jobsComplete: Long) {
		this.jobsComplete = jobsComplete
	}

	override fun getJobsCount(): Long = jobsCount

	fun setJobsCount(jobsCount: Long) {
		this.jobsCount = jobsCount
	}

	override fun getJobsSkipped(): Long = jobsCount - jobsComplete

	override fun getTime(): Long = execTime

	/** 根据当前时间刷新执行耗时。 */
	fun updateExecTime() {
		this.execTime = System.currentTimeMillis() - startTime
	}

	override fun getDelay(unit: TimeUnit): Long = unit.convert(nextUpdate.get() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)

	override fun compareTo(other: Delayed): Int = nextUpdate.get().compareTo((other as InternalTask).nextUpdate.get())

	override fun toString(): String = "InternalTask{" + bgTask.getTitle() + ", status=" + status +
		", progress=" + jobsComplete + " of " + jobsCount + '}'
}
