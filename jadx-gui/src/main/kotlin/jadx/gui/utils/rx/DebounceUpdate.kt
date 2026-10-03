package jadx.gui.utils.rx

import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.FlowableEmitter
import io.reactivex.rxjava3.core.FlowableOnSubscribe
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit

/**
 * 防抖更新器（基于 RxJava）。
 *
 * **做什么**：把频繁触发的 [requestUpdate] 合并为一次 [action] 调用，
 * 间隔为构造时指定的毫秒数（使用 RxJava 的 `debounce` 操作符）。
 *
 * **为什么不是协程**：保持原有 RxJava 线程模型不变。
 */
class DebounceUpdate(timeMs: Int, action: Runnable) {

	private var emitter: FlowableEmitter<Boolean>? = null
	private val disposable: Disposable

	init {
		val source = FlowableOnSubscribe<Boolean> { emitter -> this.emitter = emitter }
		disposable = Flowable.create(source, BackpressureStrategy.LATEST)
			.debounce(timeMs.toLong(), TimeUnit.MILLISECONDS)
			.subscribe { action.run() }
	}

	/** 请求一次（防抖后的）更新。 */
	fun requestUpdate() {
		emitter?.onNext(true)
	}

	/** 取消订阅，停止后续更新。 */
	fun dispose() {
		disposable.dispose()
	}
}
