package jadx.gui.jobs

/**
 * 任务信息只读接口。
 *
 * **做什么**：向 `onDone` / `onFinish` 回调暴露任务结束时的统计信息：
 * 状态、任务总数、已完成数、被跳过数以及执行耗时。
 *
 * **为什么使用属性形态**：接口属性在 JVM 上仍生成 `getXxx()`，Java 实现方
 * （如 `SearchTask`）与调用方（如 `SearchDialog`）无需任何改动。
 */
interface ITaskInfo {

	/** 当前任务状态。 */
	val status: TaskStatus

	/** 计划执行的任务（job）总数。 */
	val jobsCount: Long

	/** 已完成的任务数。 */
	val jobsComplete: Long

	/** 被跳过的任务数（= 总数 - 已完成数）。 */
	val jobsSkipped: Long

	/** 任务执行耗时（毫秒）。 */
	val time: Long
}
