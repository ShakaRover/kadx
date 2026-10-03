package jadx.gui.report

import jadx.api.JadxDecompiler
import jadx.cli.config.JadxConfigAdapter
import jadx.commons.app.JadxSystemInfo
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.settings.JadxSettings
import jadx.gui.settings.JadxSettingsData
import jadx.gui.ui.MainWindow
import jadx.gui.utils.LafManager
import jadx.gui.utils.Link
import jadx.gui.utils.NLS
import jadx.gui.utils.TextStandardActions
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * jadx 错误上报对话框。
 *
 * **做什么**：展示异常堆栈与环境信息（版本、Java/OS、堆大小、命令行），
 * 并生成一个预填好标题与正文的 GitHub「新建 issue」链接。
 *
 * **线程模型**：保持原 Swing 模型——[show] 通过 [UiUtils.uiRun] 在 EDT 上创建对话框，
 * 滚动条归零也用 `SwingUtilities.invokeLater`。
 */
class ExceptionDialog private constructor(mainWindow: MainWindow?, data: ExceptionData) : JDialog(mainWindow, NLS.str("error.dialog.jadx_error_title")) {

	init {
		val titlePanel = JPanel(BorderLayout())
		val titleLabel = JLabel("<html><h2>" + NLS.str("error.dialog.exception_title") + "</h2></html>")
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER)
		titlePanel.add(titleLabel, BorderLayout.CENTER)

		val details: MutableMap<String, String> = LinkedHashMap()
		details["Jadx version"] = JadxDecompiler.getVersion()
		details["Java version"] = JadxSystemInfo.JAVA_VER
		details["Java VM"] = String.format(
			"%s %s",
			System.getProperty("java.vm.vendor", "?"),
			System.getProperty("java.vm.name", "?"),
		)
		details["Platform"] = String.format(
			"%s (%s %s)",
			JadxSystemInfo.OS_NAME,
			JadxSystemInfo.OS_VERSION,
			JadxSystemInfo.OS_ARCH,
		)
		details["Max heap size"] = String.format("%d MB", Runtime.getRuntime().maxMemory() / (1024 * 1024))

		try {
			details["Command line"] = ProcessHandle.current().info().commandLine().orElse("")
		} catch (t: Throwable) {
			LOG.error("Failed to get program command line", t)
		}
		val stackTrace = Utils.getFullStackTrace(data.getException())
		val issueLink = buildNewIssueLink(data, details, stackTrace)

		val messageArea = JTextArea()
		TextStandardActions.attach(messageArea)
		messageArea.isEditable = false
		if (mainWindow != null) {
			messageArea.font = mainWindow.getSettings().getCodeFont()
		}
		messageArea.foreground = Color.BLACK
		messageArea.background = Color.WHITE

		val detailsTextBuilder = StringBuilder()
		details.forEach { (key, value) ->
			detailsTextBuilder.append(String.format("%" + FMT_DETAIL_LENGTH + "s: %s\n", key, value))
		}
		messageArea.setText(detailsTextBuilder.toString() + "\n" + stackTrace)

		val messageAreaScroller = JScrollPane(messageArea)
		messageAreaScroller.minimumSize = Dimension(600, 400)
		messageAreaScroller.preferredSize = Dimension(600, 400)

		val exitButton = JButton(NLS.str("error.dialog.terminate"))
		exitButton.addActionListener { System.exit(1) }
		val closeButton = JButton(NLS.str("common_dialog.close"))
		closeButton.addActionListener {
			isVisible = false
			dispose()
		}

		val buttonPanel = JPanel()
		buttonPanel.setLayout(BoxLayout(buttonPanel, BoxLayout.LINE_AXIS))
		if (issueLink != null) {
			buttonPanel.add(issueLink)
		}
		buttonPanel.add(Box.createHorizontalGlue())
		buttonPanel.add(exitButton)
		buttonPanel.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPanel.add(closeButton)

		val contentPanel = JPanel()
		contentPanel.setLayout(BorderLayout(5, 5))
		contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		contentPanel.add(titlePanel, BorderLayout.PAGE_START)
		contentPanel.add(messageAreaScroller, BorderLayout.CENTER)
		contentPanel.add(buttonPanel, BorderLayout.PAGE_END)
		getContentPane().add(contentPanel)
		pack()

		SwingUtilities.invokeLater { messageAreaScroller.getVerticalScrollBar().setValue(0) }

		val toolkit = Toolkit.getDefaultToolkit()
		val screenSize = toolkit.getScreenSize()
		val x = (screenSize.width - getWidth()) / 2
		val y = (screenSize.height - getHeight()) / 2
		setLocation(x, y)

		getRootPane().registerKeyboardAction(
			{ isVisible = false },
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW,
		)

		defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
		isVisible = true
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ExceptionDialog::class.java)

		private const val FMT_DETAIL_LENGTH = "-13"

		@JvmStatic
		fun show(mainWindow: MainWindow?, data: ExceptionData) {
			UiUtils.uiRun { ExceptionDialog(mainWindow, data) }
		}

		/** 构造 GitHub 新建 issue 的链接；无可用项目时返回 null。 */
		private fun buildNewIssueLink(data: ExceptionData, details: Map<String, String>, stackTrace: String): Link? {
			val project = data.getGithubProject()
			if (project.isEmpty()) {
				return null
			}
			val ex = data.getException()
			val issueTitle = try {
				URLEncoder.encode(ex.toString(), StandardCharsets.UTF_8)
			} catch (e: Exception) {
				LOG.error("URL encoding of title failed", e)
				ex.javaClass.simpleName
			}

			val message = "Please describe what you did before the error occurred.\n\n" +
				"**IMPORTANT!** If the error occurs with a specific APK file please attach or provide link to apk file!\n\n"

			val detailsIssueBuilder = StringBuilder()
			details.forEach { (key, value) -> detailsIssueBuilder.append(String.format("* %s: %s\n", key, value)) }

			val body = String.format("%s%s\n```\n%s\n```", message, detailsIssueBuilder, stackTrace)

			val issueBody = try {
				URLEncoder.encode(body, StandardCharsets.UTF_8)
			} catch (e: Exception) {
				LOG.error("URL encoding of body failed", e)
				"Please copy the displayed text in the Jadx error dialog and paste it here"
			}
			val url = String.format(
				"https://github.com/%s/issues/new?labels=bug&title=%s&body=%s",
				project,
				issueTitle,
				issueBody,
			)
			return Link("<html><u><b>" + NLS.str("error.dialog.new_github_issue") + "</b></u></html>", url)
		}

		/** 抛出一个嵌套异常，供「查看错误对话框」菜单项测试用。 */
		@JvmStatic
		fun throwTestException() {
			try {
				throw RuntimeException("Inner exception message")
			} catch (e: Exception) {
				throw JadxRuntimeException("Outer exception message", e)
			}
		}

		/** 触发一次测试异常并弹出错误对话框。 */
		@JvmStatic
		fun showTestExceptionDialog() {
			try {
				throwTestException()
			} catch (e: Exception) {
				val excData = ExceptionData(e, JadxExceptionHandler.MAIN_PROJECT_STRING, null)
				ExceptionDialog.show(null, excData)
			}
		}

		/** 独立启动入口：加载设置后展示测试错误对话框。 */
		@JvmStatic
		fun main(args: Array<String>) {
			val configAdapter: JadxConfigAdapter<JadxSettingsData> = JadxSettings.buildConfigAdapter()
			configAdapter.useConfigRef("")
			val settingsData = configAdapter.load()
			if (settingsData != null) {
				val settings = JadxSettings(configAdapter)
				settings.loadSettingsData(settingsData)
				LafManager.init(settings)
			}
			showTestExceptionDialog()
		}
	}
}
