package jadx.gui.ui.dialog

import jadx.gui.ui.MainWindow
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import javax.swing.JDialog
import javax.swing.WindowConstants

/**
 * 所有普通对话框的基类：统一窗口初始化与位置/大小持久化。
 *
 * **做什么**：提供 [commonWindowInit]（模态、ESC 关闭、记忆窗口位置）与
 * 覆写的 [dispose]（关闭前保存窗口位置）。
 *
 * **为什么 `mainWindow` 用 `@JvmField`**：Java 子类（如 `ExportProjectDialog`）
 * 仍按字段方式访问 `mainWindow`，因此必须生成同名 `protected` 字段。
 */
abstract class CommonDialog(
	@JvmField protected val mainWindow: MainWindow,
) : JDialog(mainWindow) {

	/**
	 * 公共窗口初始化：设置为应用模态、ESC 关闭、记忆并恢复窗口位置/大小。
	 */
	protected fun commonWindowInit() {
		setModalityType(ModalityType.APPLICATION_MODAL)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
		UiUtils.addEscapeShortCutToDispose(this)
		setLocationRelativeTo(null)

		// 先 pack 取得首选大小，再据此设置最小尺寸
		UiUtils.uiRunAndWait { pack() }
		val minSize: Dimension = size
		minimumSize = minSize
		if (!mainWindow.getSettings().loadWindowPos(this)) {
			setSize(incByPercent(minSize.getWidth(), 30), incByPercent(minSize.getHeight(), 30))
		}
	}

	override fun dispose() {
		try {
			mainWindow.getSettings().saveWindowPos(this)
		} catch (e: Exception) {
			LOG.warn("Failed to save window size and position", e)
		}
		super.dispose()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CommonDialog::class.java)

		/** 按百分比放大一个数值（用于给未保存过位置的窗口一个更宽松的初始尺寸）。 */
		private fun incByPercent(value: Double, percent: Int): Int = (value * (1 + percent * 0.01)).toInt()
	}
}
