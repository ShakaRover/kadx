package jadx.gui.utils.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 防抖更新器（N1c：替代原 `utils/rx/DebounceUpdate`）。
 *
 * **做什么**：把频繁触发的 [requestUpdate] 合并为一次 [action] 调用，
 * 间隔为构造时指定的毫秒数（使用 Flow 的 `debounce` 操作符）。
 *
 * **线程模型**：防抖计时在 `Dispatchers.Default` 上，[action] 回到
 * `Dispatchers.Swing` 执行（UI 更新必须在 EDT）。
 *
 * **生命周期**：持有自己的 [CoroutineScope]（`SupervisorJob`），
 * 调用方必须在销毁时调用 [dispose]。
 */
@OptIn(FlowPreview::class)
class DebounceUpdate(private val timeMs: Int, private val action: () -> Unit) {

	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

	init {
		scope.launch {
			requests.debounce(timeMs.milliseconds).collect {
				withContext(Dispatchers.Swing) { action() }
			}
		}
	}

	/** 请求一次（防抖后的）更新。 */
	fun requestUpdate() {
		requests.tryEmit(Unit)
	}

	/** 取消作用域，停止后续更新。 */
	fun dispose() {
		scope.cancel()
	}
}
