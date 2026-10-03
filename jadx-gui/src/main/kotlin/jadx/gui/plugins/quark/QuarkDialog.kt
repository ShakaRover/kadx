package jadx.gui.plugins.quark

import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.NodeLabel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.WindowConstants

/**
 * Quark 分析入口对话框：选择要分析的 APK/DEX 文件。
 *
 * **做什么**：过滤当前工程里后缀为 `.apk` / `.dex` 的文件，让用户选择其一，
 * 点击 Start 后交由 [QuarkManager] 在后台执行分析。
 *
 * **线程模型**：保持原 Swing 模型，对话框为应用模态。
 */
class QuarkDialog(@Transient private val mainWindow: MainWindow) : JDialog() {

	@Transient
	private val settings: JadxSettings = mainWindow.getSettings()

	private val files: List<Path> = filterOpenFiles(mainWindow)

	private lateinit var fileSelectCombo: JComboBox<Path>

	init {
		if (files.isEmpty()) {
			UiUtils.errorMessage(mainWindow, "Quark is unable to analyze loaded files")
			LOG.error("Quark: The files cannot be analyzed: {}", mainWindow.getProject().getFilePaths())
		} else {
			initUI()
		}
	}

	private fun filterOpenFiles(mainWindow: MainWindow): List<Path> {
		val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{apk,dex}")
		return mainWindow.getProject().getFilePaths().filter { matcher.matches(it) }
	}

	private fun initUI() {
		val description = JLabel("Analyzing apk using Quark-Engine")
		val selectApkText = JLabel("Select Apk/Dex")
		description.setAlignmentX(0.5f)

		fileSelectCombo = JComboBox(files.toTypedArray())
		fileSelectCombo.setRenderer(object : ListCellRenderer<Path> {
			override fun getListCellRendererComponent(
				list: JList<out Path>?,
				value: Path?,
				index: Int,
				isSelected: Boolean,
				cellHasFocus: Boolean,
			): Component = NodeLabel(checkNotNull(value).getFileName().toString())
		})

		val textPane = JPanel()
		textPane.add(description)

		val selectApkPanel = JPanel()
		selectApkPanel.add(selectApkText)
		selectApkPanel.add(fileSelectCombo)

		val buttonPane = JPanel()
		val start = JButton("Start")
		val close = JButton("Close")
		close.addActionListener { close() }
		start.addActionListener { startQuarkTasks() }
		buttonPane.add(start)
		buttonPane.add(close)
		rootPane.setDefaultButton(close)

		val centerPane = JPanel()
		centerPane.add(selectApkPanel)
		val contentPane: Container = getContentPane()

		contentPane.add(textPane, BorderLayout.PAGE_START)
		contentPane.add(centerPane)
		contentPane.add(buttonPane, BorderLayout.PAGE_END)

		title = "Quark Engine"
		pack()
		if (!mainWindow.getSettings().loadWindowPos(this)) {
			setSize(300, 140)
		}
		setLocationRelativeTo(null)
		defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
		modalityType = Dialog.ModalityType.APPLICATION_MODAL
		UiUtils.addEscapeShortCutToDispose(this)
	}

	private fun startQuarkTasks() {
		val apkFile = fileSelectCombo.getSelectedItem() as Path
		QuarkManager(mainWindow, apkFile).start()
		close()
	}

	private fun close() {
		dispose()
	}

	override fun dispose() {
		settings.saveWindowPos(this)
		super.dispose()
	}

	companion object {
		private const val serialVersionUID = 4855753773520368215L

		private val LOG = LoggerFactory.getLogger(QuarkDialog::class.java)
	}
}
