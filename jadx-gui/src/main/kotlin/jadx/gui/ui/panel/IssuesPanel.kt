package jadx.gui.ui.panel

import ch.qos.logback.classic.Level
import jadx.gui.logs.IssuesListener
import jadx.gui.logs.LogCollector
import jadx.gui.logs.LogOptions
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 底部状态栏中的「问题」提示面板。
 *
 * **做什么**：监听日志收集器（[LogCollector]）中 ERROR/WARN 级别的日志数量，
 * 在界面上显示两个可点击的图标；点击图标会打开对应级别的日志查看器。
 *
 * **线程模型**：日志计数由 [IssuesListener] 在 `Dispatchers.Swing`（EDT）上回调。
 */
class IssuesPanel(private val mainWindow: MainWindow) : JPanel() {

	private val issuesListener: IssuesListener
	private lateinit var errorLabel: JLabel
	private lateinit var warnLabel: JLabel

	init {
		initUI()
		issuesListener = IssuesListener(this)
		LogCollector.instance.registerListener(issuesListener)
	}

	/** 已记录的错误数量。 */
	val errorsCount: Int get() = issuesListener.getErrors()

	private fun initUI() {
		val label = JLabel(NLS.str("issues_panel.label"))
		errorLabel = JLabel(ERROR_ICON)
		warnLabel = JLabel(WARN_ICON)

		val toolTipText = NLS.str("issues_panel.tooltip")
		errorLabel.setToolTipText(toolTipText)
		warnLabel.setToolTipText(toolTipText)

		errorLabel.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				mainWindow.showLogViewer(LogOptions.allWithLevel(Level.ERROR))
			}
		})
		warnLabel.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				mainWindow.showLogViewer(LogOptions.allWithLevel(Level.WARN))
			}
		})

		border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
		layout = BoxLayout(this, BoxLayout.X_AXIS)
		isVisible = false
		add(label)
		add(Box.createHorizontalGlue())
		add(errorLabel)
		add(Box.createHorizontalGlue())
		add(warnLabel)
	}

	/** 刷新错误/警告计数显示；两者都为 0 时隐藏面板。 */
	fun onUpdate(error: Int, warnings: Int) {
		if (error == 0 && warnings == 0) {
			isVisible = false
			return
		}
		isVisible = true
		errorLabel.setText(NLS.str("issues_panel.errors", error))
		errorLabel.setVisible(error != 0)
		warnLabel.setText(NLS.str("issues_panel.warnings", warnings))
		warnLabel.setVisible(warnings != 0)
	}

	companion object {
		private val ERROR_ICON: ImageIcon = UiUtils.openSvgIcon("ui/error")
		private val WARN_ICON: ImageIcon = UiUtils.openSvgIcon("ui/warning")
	}
}
