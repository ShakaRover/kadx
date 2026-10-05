package kadx.gui.ui

import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.FocusManager
import javax.swing.JProgressBar

/**
 * 堆内存使用量进度条。
 *
 * **做什么**：定时（每 2 秒）采样 JVM 堆使用量，再回到 EDT 刷新进度条；
 * 应用窗口不活跃时跳过刷新；点击进度条会触发一次 GC。
 *
 * **线程模型（N1c 协程化）**：采样在 `Dispatchers.Default` 上执行，UI 更新回到
 * [scope] 的 `Dispatchers.Swing`；定时器是一个可取消的 [Job]，
 * 通过 `flow { delay(..) }` + `distinctUntilChanged` 复刻原 RxJava 的语义。
 *
 * **注意**：`SKIP_UPDATE` 是哨兵对象，必须用引用比较 `!==` 判断，不能用 `!=`。
 */
class HeapUsageBar : JProgressBar() {

	@Transient
	private val runtime: Runtime = Runtime.getRuntime()

	@Transient
	private val focusManager: FocusManager = FocusManager.getCurrentManager()

	private val maxGB: Double
	private val limit: Long
	private var peakUsed: Long = 0
	private val labelTemplate: String

	/** 协程作用域：随组件生命周期（[removeNotify]）取消，禁止使用 GlobalScope。 */
	@Transient
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

	@Transient
	private var timer: Job? = null

	@Transient
	private var currentColor: Color? = null

	init {
		isBorderPainted = false
		isStringPainted = true

		val maxMemory = runtime.maxMemory()
		peakUsed = 0
		maxGB = maxMemory / GB
		limit = maxMemory - UiUtils.MIN_FREE_MEMORY
		labelTemplate = NLS.str("heapUsage.text")

		maximum = (maxMemory / 1024).toInt()
		setColor(GREEN)

		addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				Runtime.getRuntime().gc()
				update()
				if (LOG.isDebugEnabled) {
					LOG.debug("Memory used: {}", UiUtils.memoryInfo())
				}
			}
		})
	}

	override fun setVisible(enabled: Boolean) {
		super.setVisible(enabled)
		if (enabled) {
			startTimer()
		} else {
			reset()
		}
	}

	override fun removeNotify() {
		reset()
		scope.cancel()
		super.removeNotify()
	}

	/** 一次采样结果（值、显示文本、颜色）。 */
	private class UpdateData {
		var value: Int = 0
		var label: String? = null
		var color: Color? = null
	}

	private fun startTimer() {
		if (timer != null) {
			return
		}
		update()
		timer = scope.launch {
			flow {
				while (currentCoroutineContext().isActive) {
					delay(UPDATE_INTERVAL_MS)
					emit(withContext(Dispatchers.Default) { prepareUpdate() })
				}
			}
				.filter { update -> update !== SKIP_UPDATE }
				.distinctUntilChanged { a, b -> a.label == b.label } // 仅在 label 变化时放行
				.collect { update -> applyUpdate(update) }
		}
	}

	private fun prepareUpdate(): UpdateData {
		if (focusManager.activeWindow == null) {
			// 应用窗口不活跃时跳过更新
			return SKIP_UPDATE
		}
		val updateData = UpdateData()
		val used = runtime.totalMemory() - runtime.freeMemory()
		if (used > peakUsed) {
			peakUsed = used
		}
		updateData.value = (used / 1024).toInt()
		updateData.label = String.format(labelTemplate, used / GB, maxGB, peakUsed / GB)
		updateData.color = if (used > limit) RED else GREEN
		return updateData
	}

	private fun applyUpdate(update: UpdateData) {
		value = update.value
		string = update.label
		setColor(update.color)
	}

	private fun setColor(color: Color?) {
		if (currentColor !== color) {
			foreground = color
			currentColor = color
		}
	}

	private fun update() {
		val update = prepareUpdate()
		if (update !== SKIP_UPDATE) {
			applyUpdate(update)
		}
	}

	fun reset() {
		timer?.cancel()
		timer = null
	}

	companion object {
		private const val serialVersionUID: Long = -8739563124249884967L

		private val LOG: Logger = LoggerFactory.getLogger(HeapUsageBar::class.java)

		private const val GB: Double = 1024 * 1024 * 1024.0

		/** 采样间隔（毫秒）。 */
		private const val UPDATE_INTERVAL_MS = 2000L

		private val GREEN: Color = Color(0, 180, 0)
		private val RED: Color = Color(200, 0, 0)

		private val SKIP_UPDATE: UpdateData = UpdateData()
	}
}
