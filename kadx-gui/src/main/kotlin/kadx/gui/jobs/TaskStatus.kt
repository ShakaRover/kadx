package kadx.gui.jobs

/**
 * 后台任务的状态枚举。
 *
 * **做什么**：描述任务从等待、执行到结束（正常完成或各种取消）的生命周期。
 * [InternalTask] 与 UI 层都依据该状态决定后续行为（例如是否弹出“内存不足”提示）。
 *
 * **为什么保持普通 `enum class`**：枚举常量天然是单例，按身份比较即可，
 * 与原 Java 的 `enum` 语义完全一致（序数、`values()`、`valueOf()` 均不变）。
 */
enum class TaskStatus {
	/** 已创建但尚未开始执行。 */
	WAIT,

	/** 已开始执行。 */
	STARTED,

	/** 正常完成。 */
	COMPLETE,

	/** 被用户主动取消。 */
	CANCEL_BY_USER,

	/** 超过时间上限被强制取消。 */
	CANCEL_BY_TIMEOUT,

	/** 内存不足被强制取消。 */
	CANCEL_BY_MEMORY,

	/** 执行过程中出错。 */
	ERROR,
}
