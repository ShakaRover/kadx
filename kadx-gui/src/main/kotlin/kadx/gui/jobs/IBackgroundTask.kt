package kadx.gui.jobs

import kadx.api.utils.tasks.ITaskExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 后台任务接口。
 *
 * **做什么**：描述一个可由 [BackgroundExecutor] 调度的任务：提供标题、把工作
 * 拆成 [ITaskExecutor] 的“阶段”，并在完成前后触发回调。
 *
 * **生命周期回调**：
 * - [onDone]：所有 job 结束后在**执行线程**上调用；
 * - [onFinish]：随后在 **EDT（事件分发线程）** 上调用，可安全操作 Swing 组件。
 *
 * **为什么使用属性形态**：接口属性在 JVM 上仍生成 `getXxx()` / `isXxx()`，本接口
 * 被大量实现（`SearchTask`、`SimpleTask`、`TaskWithExtraOnFinish` 等），方法名保持不变；
 * 默认实现依赖 Kotlin 的接口默认方法（`jvm-default=enable`），实现方无需重写全部成员。
 */
interface IBackgroundTask : Cancelable {

	/** 任务标题（显示在进度条上）。 */
	val title: String

	/** 把任务拆分为可执行的阶段并返回执行器。 */
	fun scheduleTasks(): ITaskExecutor

	/** 所有 job 结束后在执行线程上调用（默认空实现）。 */
	fun onDone(taskInfo: ITaskInfo) {
	}

	/** 所有 job 结束后在 EDT 上调用（默认空实现）。 */
	fun onFinish(taskInfo: ITaskInfo) {
	}

	/** 是否允许用户取消，默认否。 */
	fun canBeCanceled(): Boolean = false

	/** 全局时间上限（毫秒，0 表示不限制），默认 0。 */
	fun timeLimit(): Int = 0

	/** 执行器是否在每个 tick 检查内存并在不足时取消，默认否。 */
	fun checkMemoryUsage(): Boolean = false

	/** 自定义任务进度（可选），默认 `null` 表示由执行器统计。 */
	val taskProgress: ITaskProgress? get() = null

	/** 进度通知流（可选），默认无进度更新。 */
	val progressFlow: Flow<ITaskProgress> get() = emptyFlow()

	/** 是否为静默任务（不显示进度），默认否。 */
	val isSilent: Boolean get() = false
}
