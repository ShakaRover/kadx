package jadx.gui.jobs

import jadx.gui.utils.UiUtils

/**
 * [ITaskProgress] 的简单可变实现。
 *
 * **做什么**：保存“当前进度 / 总量”，供进度条读取；`SearchTask` 等任务
 * 会在执行过程中调用 [updateProgress] / [updateTotal] 刷新数值。
 *
 * **构造函数**：
 * - `TaskProgress()`：进度 0，总量 100；
 * - `TaskProgress(long, long)`：先用 [UiUtils.calcProgress] 换算为百分比（总量固定 100）；
 * - `TaskProgress(int, int)`：直接使用给定数值。
 *
 * **为什么不用 `data class`**：任务对象需要按引用比较。
 */
class TaskProgress(
	private var progressValue: Int,
	private var totalValue: Int,
) : ITaskProgress {

	/** 默认进度 0%，总量 100。 */
	constructor() : this(0, 100)

	/** 由原始 long 值换算为百分比（内部总量固定为 100）。 */
	constructor(progress: Long, total: Long) : this(UiUtils.calcProgress(progress, total), 100)

	override fun progress(): Int = progressValue

	override fun total(): Int = totalValue

	/** 更新当前进度。 */
	fun updateProgress(progress: Int) {
		progressValue = progress
	}

	/** 更新总量。 */
	fun updateTotal(total: Int) {
		totalValue = total
	}

	override fun toString(): String = "TaskProgress{$progressValue of $totalValue}"
}
