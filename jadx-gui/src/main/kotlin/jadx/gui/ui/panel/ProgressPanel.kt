package jadx.gui.ui.panel

import jadx.gui.jobs.ITaskProgress
import jadx.gui.ui.MainWindow
import jadx.gui.utils.Icons
import jadx.gui.utils.UiUtils
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar

/**
 * 后台任务进度条面板。
 *
 * **做什么**：显示当前后台任务名称、进度百分比与取消按钮；
 * 进度为 0 或总量未知时显示为不确定（indeterminate）模式。
 *
 * **线程模型**：由 `ProgressUpdater` 在 EDT 上驱动，保持原 Swing 模型。
 */
class ProgressPanel(private val mainWindow: MainWindow, private val showCancelButton: Boolean) : JPanel() {

	private val progressBar: JProgressBar
	private val progressLabel: JLabel
	private val cancelButton: JButton

	init {
		progressLabel = JLabel()
		progressBar = JProgressBar(0, 100)
		progressBar.setIndeterminate(true)
		progressBar.setStringPainted(false)
		progressLabel.setLabelFor(progressBar)

		border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
		layout = BoxLayout(this, BoxLayout.X_AXIS)
		isVisible = false
		add(progressLabel)
		add(progressBar)

		val cancelIcon = Icons.ICON_CLOSE
		cancelButton = JButton(cancelIcon)
		cancelButton.setPreferredSize(Dimension(cancelIcon.getIconWidth(), cancelIcon.getIconHeight()))
		cancelButton.setToolTipText("Cancel background jobs")
		cancelButton.setBorderPainted(false)
		cancelButton.setFocusPainted(false)
		cancelButton.setContentAreaFilled(false)
		cancelButton.addActionListener { mainWindow.cancelBackgroundJobs() }
		cancelButton.setVisible(showCancelButton)
		add(cancelButton)
	}

	/** 重置为不确定进度并清空百分比文本。 */
	fun reset() {
		cancelButton.setVisible(showCancelButton)
		progressBar.setIndeterminate(true)
		progressBar.setValue(0)
		progressBar.setString("")
		progressBar.setStringPainted(true)
	}

	/** 根据任务进度刷新进度条。 */
	fun setProgress(taskProgress: ITaskProgress) {
		val progress = taskProgress.progress()
		val total = taskProgress.total()
		if (progress == 0 || total == 0) {
			progressBar.setIndeterminate(true)
		} else {
			if (progressBar.isIndeterminate()) {
				progressBar.setIndeterminate(false)
			}
			setProgress(UiUtils.calcProgress(progress.toLong(), total.toLong()))
		}
	}

	private fun setProgress(progress: Int) {
		progressBar.setIndeterminate(false)
		progressBar.setValue(progress)
		progressBar.setString("$progress%")
		progressBar.setStringPainted(true)
	}

	/** 设置进度条左侧的说明文本。 */
	fun setLabel(label: String) {
		progressLabel.setText(label)
	}

	/** 切换不确定进度模式。 */
	fun setIndeterminate(newValue: Boolean) {
		progressBar.setIndeterminate(newValue)
	}

	/** 控制取消按钮的可见性。 */
	fun setCancelButtonVisible(visible: Boolean) {
		cancelButton.setVisible(visible)
	}
}
