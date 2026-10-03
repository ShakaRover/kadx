package jadx.gui.ui

import hu.akarnokd.rxjava3.swing.SwingSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Objects
import java.util.concurrent.TimeUnit
import javax.swing.FocusManager
import javax.swing.JProgressBar

/**
 * 堆内存使用量进度条。
 *
 * **做什么**：定时（每 2 秒）在后台线程采样 JVM 堆使用量，再切回 EDT 刷新进度条；
 * 应用窗口不活跃时跳过刷新；点击进度条会触发一次 GC。
 *
 * **为什么保留 RxJava 与 Swing 线程模型**：本阶段只做语法迁移，采样仍走
 * `Schedulers.newThread()` + `SwingSchedulers.edt()`，不引入协程。
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

	@Transient
	private var timer: Disposable? = null

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
		timer = Flowable.interval(2L, TimeUnit.SECONDS, Schedulers.newThread())
			.map { prepareUpdate() }
			.filter { update -> update !== SKIP_UPDATE }
			.distinctUntilChanged { a, b -> a.label == b.label } // 仅在 label 变化时放行
			.subscribeOn(SwingSchedulers.edt())
			.subscribe { update -> applyUpdate(update) }
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
		val timer = this.timer
		if (timer != null) {
			timer.dispose()
			this.timer = null
		}
	}

	companion object {
		private const val serialVersionUID: Long = -8739563124249884967L

		private val LOG: Logger = LoggerFactory.getLogger(HeapUsageBar::class.java)

		private const val GB: Double = 1024 * 1024 * 1024.0

		private val GREEN: Color = Color(0, 180, 0)
		private val RED: Color = Color(200, 0, 0)

		private val SKIP_UPDATE: UpdateData = UpdateData()
	}
}
