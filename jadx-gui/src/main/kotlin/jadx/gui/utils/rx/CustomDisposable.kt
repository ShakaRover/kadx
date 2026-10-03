package jadx.gui.utils.rx

import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 [Runnable] 的自定义 RxJava [Disposable]。
 *
 * **做什么**：把一段“释放资源”的逻辑包装成 RxJava 的 [Disposable]，
 * 使 `Flowable.create(...)` 中注册的监听器能在取消订阅时被移除。
 *
 * **为什么不是协程**：本阶段（5.1）严格保留原 RxJava 依赖，不做协程改造。
 */
class CustomDisposable(private val disposeTask: Runnable) : Disposable {

	private val disposed = AtomicBoolean(false)

	override fun dispose() {
		try {
			disposeTask.run()
		} finally {
			disposed.set(true)
		}
	}

	override fun isDisposed(): Boolean = disposed.get()
}
