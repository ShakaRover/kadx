package kadx.gui.jobs

/**
 * 反编译任务的结果快照。
 *
 * **做什么**：记录本次反编译被跳过的类数量、最终状态与时间上限，
 * 供 `DecompileTask` 在 `onDone` 后决定是否提示用户。
 *
 * **为什么不用 `data class`**：保持与 Java 原类一致的“按引用比较”语义
 * （不自动生成 `equals` / `hashCode` / `copy`），getter 也保持显式函数。
 */
class ProcessResult(
	private val skipped: Int,
	private val status: TaskStatus,
	private val timeLimit: Int,
) {

	/** 被跳过的类数量。 */
	fun getSkipped(): Int = skipped

	/** 任务结束状态。 */
	fun getStatus(): TaskStatus = status

	/** 本次任务的时间上限（毫秒）。 */
	fun getTimeLimit(): Int = timeLimit
}
