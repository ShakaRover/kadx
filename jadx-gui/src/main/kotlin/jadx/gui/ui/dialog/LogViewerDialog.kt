package jadx.gui.ui.dialog

import jadx.gui.logs.LogOptions
import jadx.gui.logs.LogPanel
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import java.awt.BorderLayout
import java.awt.Container
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

/**
 * 日志查看器窗口（独立 [JFrame]）。
 *
 * **做什么**：单例式打开日志面板；若已存在则复用并置前。支持把日志「停靠」回主窗口。
 *
 * **为什么保持单例静态字段**：原 Java 用静态 `openLogDialog` 记录唯一实例，
 * 这里放在 `companion object` 并保留 `@JvmStatic` 入口。
 */
class LogViewerDialog private constructor(
	mainWindow: MainWindow,
	logOptions: LogOptions,
) : JFrame() {

	private val settings: JadxSettings = mainWindow.getSettings()
	private val logPanel: LogPanel

	init {
		UiUtils.setWindowIcons(this)

		// 点击「停靠」时：保存设置 -> 关闭本窗口 -> 让主窗口内嵌日志面板
		val dock = Runnable {
			mainWindow.getSettings().saveDockLogViewer(true)
			dispose()
			mainWindow.showLogViewer(LogOptions.current())
		}
		logPanel = LogPanel(mainWindow, logOptions, dock) { dispose() }
		val contentPane: Container = getContentPane()
		contentPane.add(logPanel, BorderLayout.CENTER)

		title = NLS.str("log_viewer.title")
		pack()
		setSize(800, 600)
		setDefaultCloseOperation(DISPOSE_ON_CLOSE)
		setLocationRelativeTo(null)
		settings.loadWindowPos(this)
		addWindowListener(object : WindowAdapter() {
			override fun windowClosing(e: WindowEvent) {
				openLogDialog = null
			}
		})
	}

	override fun dispose() {
		logPanel.dispose()
		settings.saveWindowPos(this)
		super.dispose()
	}

	companion object {
		private const val serialVersionUID = -2188700277429054641L

		private var openLogDialog: LogViewerDialog? = null

		/**
		 * 打开（或复用）日志查看器。
		 *
		 * @param mainWindow 主窗口
		 * @param logOptions 初始日志过滤选项
		 */
		fun open(mainWindow: MainWindow, logOptions: LogOptions) {
			val logDialog: LogViewerDialog
			val current = openLogDialog
			if (current != null) {
				logDialog = current
			} else {
				logDialog = LogViewerDialog(mainWindow, logOptions)
				openLogDialog = logDialog
			}
			logDialog.isVisible = true
			logDialog.toFront()
		}
	}
}
