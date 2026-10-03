package jadx.gui.jobs

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 可取消后台任务抽象基类。
 *
 * **做什么**：用 [AtomicBoolean] 保存取消标志，为子类提供默认的
 * [isCanceled] / [cancel] / [canBeCanceled] 实现，并额外提供 [resetCancel]
 * 供可复用的任务（如 `SearchTask`）在重新开始前清除取消状态。
 *
 * **为什么不用 `data class`**：任务是身份对象，需要按引用比较。
 */
abstract class CancelableBackgroundTask : IBackgroundTask {

	/** 取消标志，使用原子类型保证多线程可见性。 */
	private val cancel = AtomicBoolean(false)

	override val isCanceled: Boolean get() = cancel.get()

	override fun cancel() {
		cancel.set(true)
	}

	/** 清除取消标志，便于任务对象被重复使用。 */
	fun resetCancel() {
		cancel.set(false)
	}

	override fun canBeCanceled(): Boolean = true
}
