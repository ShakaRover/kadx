package jadx.gui.jobs

/**
 * 任务信息只读接口。
 *
 * **做什么**：向 `onDone` / `onFinish` 回调暴露任务结束时的统计信息：
 * 状态、任务总数、已完成数、被跳过数以及执行耗时。
 *
 * **为什么保留 `getXxx` 函数形态**：原 Java 接口即如此，Java 实现方
 * （如 `SearchTask`）与调用方（如 `SearchDialog`）无需任何改动。
 */
interface ITaskInfo {

	/** 当前任务状态。 */
	fun getStatus(): TaskStatus

	/** 计划执行的任务（job）总数。 */
	fun getJobsCount(): Long

	/** 已完成的任务数。 */
	fun getJobsComplete(): Long

	/** 被跳过的任务数（= 总数 - 已完成数）。 */
	fun getJobsSkipped(): Long

	/** 任务执行耗时（毫秒）。 */
	fun getTime(): Long
}
