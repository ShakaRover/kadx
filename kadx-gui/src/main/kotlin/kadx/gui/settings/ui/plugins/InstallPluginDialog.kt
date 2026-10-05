package kadx.gui.settings.ui.plugins

import com.formdev.flatlaf.FlatClientProperties
import kadx.gui.ui.MainWindow
import kadx.gui.ui.filedialog.FileDialogWrapper
import kadx.gui.ui.filedialog.FileOpenMode
import kadx.gui.utils.NLS
import kadx.gui.utils.TextStandardActions
import kadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.WindowConstants

/**
 * 「安装插件」对话框：可输入插件 location id，或选择一个本地 jar/zip 文件。
 */
class InstallPluginDialog(
	private val mainWindow: MainWindow,
	private val pluginsSettings: PluginSettings,
) : JDialog(mainWindow, NLS.str("preferences.plugins.install")) {

	private val locationFld: JTextField = JTextField()

	init {
		initComponents()
	}

	private fun initComponents() {
		locationFld.alignmentX = Component.LEFT_ALIGNMENT
		locationFld.columns = 50
		TextStandardActions.attach(locationFld)
		locationFld.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)

		val locationLbl = JLabel(NLS.str("preferences.plugins.location_id_label"))
		locationLbl.labelFor = locationFld

		val locationPanel = JPanel()
		locationPanel.layout = BoxLayout(locationPanel, BoxLayout.LINE_AXIS)
		locationPanel.add(locationLbl)
		locationPanel.add(Box.createRigidArea(Dimension(5, 0)))
		locationPanel.add(locationFld)

		val fileBtn = JButton(NLS.str("preferences.plugins.plugin_jar"))
		fileBtn.addActionListener { openPluginFile() }
		val fileLbl = JLabel(NLS.str("preferences.plugins.plugin_jar_label"))
		fileLbl.labelFor = fileBtn

		val filePanel = JPanel()
		filePanel.layout = BoxLayout(filePanel, BoxLayout.LINE_AXIS)
		filePanel.add(fileLbl)
		filePanel.add(Box.createRigidArea(Dimension(5, 0)))
		filePanel.add(fileBtn)

		val mainPanel = JPanel()
		mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
		mainPanel.add(locationPanel)
		mainPanel.add(Box.createRigidArea(Dimension(0, 5)))
		mainPanel.add(filePanel)

		val installBtn = JButton(NLS.str("preferences.plugins.install_btn"))
		installBtn.addActionListener { install() }
		val cancelBtn = JButton(NLS.str("preferences.cancel"))
		cancelBtn.addActionListener { dispose() }

		val buttonPane = JPanel()
		buttonPane.layout = BoxLayout(buttonPane, BoxLayout.LINE_AXIS)
		// TODO: 增加操作进度显示
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(installBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelBtn)
		rootPane.defaultButton = installBtn

		val contentPanel = JPanel()
		contentPanel.layout = BorderLayout(5, 5)
		contentPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
		contentPanel.add(mainPanel, BorderLayout.PAGE_START)
		contentPanel.add(buttonPane, BorderLayout.PAGE_END)
		contentPane.add(contentPanel)

		pack()
		setLocationRelativeTo(null)
		defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
		modalityType = Dialog.ModalityType.APPLICATION_MODAL
		UiUtils.addEscapeShortCutToDispose(this)
	}

	private fun openPluginFile() {
		val fd = FileDialogWrapper(mainWindow, FileOpenMode.CUSTOM_OPEN)
		fd.setTitle(NLS.str("preferences.plugins.plugin_jar"))
		fd.setFileExtList(listOf("jar", "zip"))
		fd.setSelectionMode(JFileChooser.FILES_ONLY)
		val files = fd.show()
		if (files.size == 1) {
			locationFld.text = "file:" + files[0].toAbsolutePath()
		}
	}

	private fun install() {
		pluginsSettings.install(locationFld.text)
		dispose()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(InstallPluginDialog::class.java)
		private const val serialVersionUID: Long = 5304314264730563853L
	}
}
