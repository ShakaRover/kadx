package jadx.gui.ui.export

import jadx.core.export.ExportGradle
import jadx.core.export.ExportGradleType
import jadx.core.utils.files.FileUtils
import jadx.gui.JadxWrapper
import jadx.gui.ui.MainWindow
import jadx.gui.ui.dialog.CommonDialog
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.utils.NLS
import jadx.gui.utils.TextStandardActions
import jadx.gui.utils.ui.DocumentUpdateListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * “导出工程”对话框。
 *
 * **做什么**：让用户选择导出路径、是否导出为 Gradle 工程、是否跳过资源/源码解码，
 * 确认后通过 [exportListener] 回调把 [ExportProjectProperties] 交给导出任务。
 *
 * **为什么保留 Swing 写法**：本阶段只做语法迁移，事件仍走 EDT，不引入协程。
 */
class ExportProjectDialog(
	mainWindow: MainWindow,
	private val exportListener: (ExportProjectProperties) -> Unit,
) : CommonDialog(mainWindow) {

	private val exportProjectProperties: ExportProjectProperties = ExportProjectProperties()

	init {
		initUI()
	}

	private fun initUI() {
		val contentPanel = JPanel()
		contentPanel.layout = BorderLayout(5, 5)
		contentPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
		contentPanel.add(makeContentPane(), BorderLayout.PAGE_START)
		contentPanel.add(initButtonsPanel(), BorderLayout.PAGE_END)
		contentPane.add(contentPanel)

		title = NLS.str("export_dialog.title")
		commonWindowInit()
	}

	private fun makeContentPane(): JPanel {
		val pathLbl = JLabel(NLS.str("export_dialog.save_path"))
		val pathField = JTextField()
		pathField.document.addDocumentListener(DocumentUpdateListener { setExportProjectPath(pathField) })
		pathField.text = mainWindow.getSettings().lastSaveFilePath.toString()
		TextStandardActions.attach(pathField)

		val browseButton = makeEditorBrowseButton(pathField)

		val resourceDecode = JCheckBox(NLS.str("preferences.skipResourcesDecode"))
		resourceDecode.isSelected = mainWindow.getSettings().isSkipResources
		resourceDecode.addItemListener { e ->
			exportProjectProperties.setSkipResources(e.stateChange == ItemEvent.SELECTED)
		}

		val skipSources = JCheckBox(NLS.str("preferences.skipSourcesDecode"))
		skipSources.isSelected = mainWindow.getSettings().isSkipSources
		skipSources.addItemListener { e ->
			exportProjectProperties.setSkipSources(e.stateChange == ItemEvent.SELECTED)
		}

		val exportTypeLbl = JLabel(NLS.str("export_dialog.export_gradle_type"))
		val exportTypeComboBox = JComboBox(ExportGradleType.values())
		exportTypeLbl.labelFor = exportTypeComboBox
		val initialExportType = exportGradleType
		exportProjectProperties.setExportGradleType(initialExportType)
		exportTypeComboBox.selectedItem = initialExportType
		exportTypeComboBox.addItemListener { e ->
			exportProjectProperties.setExportGradleType(e.item as ExportGradleType)
		}
		exportTypeComboBox.isEnabled = false

		val exportAsGradleProject = JCheckBox(NLS.str("export_dialog.export_gradle"))
		exportAsGradleProject.addItemListener { e ->
			val enableGradle = e.stateChange == ItemEvent.SELECTED
			exportProjectProperties.setAsGradleMode(enableGradle)
			exportTypeComboBox.isEnabled = enableGradle
			resourceDecode.isEnabled = !enableGradle
			skipSources.isEnabled = !enableGradle
		}

		val pathPanel = JPanel()
		pathPanel.layout = BoxLayout(pathPanel, BoxLayout.LINE_AXIS)
		pathPanel.alignmentX = Component.LEFT_ALIGNMENT
		pathPanel.add(pathLbl)
		pathPanel.add(Box.createRigidArea(Dimension(5, 0)))
		pathPanel.add(pathField)
		pathPanel.add(Box.createRigidArea(Dimension(5, 0)))
		pathPanel.add(browseButton)

		val typePanel = JPanel()
		typePanel.layout = BoxLayout(typePanel, BoxLayout.LINE_AXIS)
		typePanel.alignmentX = Component.LEFT_ALIGNMENT
		typePanel.add(Box.createRigidArea(Dimension(20, 0)))
		typePanel.add(exportTypeLbl)
		typePanel.add(Box.createRigidArea(Dimension(5, 0)))
		typePanel.add(exportTypeComboBox)
		typePanel.add(Box.createHorizontalGlue())

		val exportOptionsPanel = JPanel()
		exportOptionsPanel.border = BorderFactory.createTitledBorder(NLS.str("export_dialog.export_options"))
		exportOptionsPanel.layout = BoxLayout(exportOptionsPanel, BoxLayout.PAGE_AXIS)
		exportOptionsPanel.add(exportAsGradleProject)
		exportOptionsPanel.add(typePanel)
		exportOptionsPanel.add(resourceDecode)
		exportOptionsPanel.add(skipSources)

		val mainPanel = JPanel()
		mainPanel.layout = BoxLayout(mainPanel, BoxLayout.PAGE_AXIS)
		mainPanel.add(pathPanel)
		mainPanel.add(Box.createRigidArea(Dimension(0, 10)))
		mainPanel.add(exportOptionsPanel)
		return mainPanel
	}

	private val exportGradleType: ExportGradleType get() = try {
		val wrapper: JadxWrapper = mainWindow.getWrapper()
		ExportGradle.detectExportType(wrapper.rootNode, wrapper.resources)
	} catch (e: Exception) {
		LOG.warn("Failed to detect export type", e)
		ExportGradleType.AUTO
	}

	private fun setExportProjectPath(field: JTextField) {
		val path = field.text
		if (path.isNotEmpty()) {
			exportProjectProperties.setExportPath(field.text)
		}
	}

	private fun initButtonsPanel(): JPanel {
		val cancelButton = JButton(NLS.str("common_dialog.cancel"))
		cancelButton.addActionListener { dispose() }

		val exportProjectButton = JButton(NLS.str("common_dialog.ok"))
		exportProjectButton.addActionListener { exportProject() }
		rootPane.defaultButton = exportProjectButton

		val buttonPane = JPanel()
		buttonPane.layout = BoxLayout(buttonPane, BoxLayout.LINE_AXIS)
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(exportProjectButton)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelButton)
		return buttonPane
	}

	private fun makeEditorBrowseButton(textField: JTextField): JButton {
		val button = JButton(NLS.str("export_dialog.browse"))
		button.addActionListener {
			val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.EXPORT)
			fileDialog.currentDir?.let { mainWindow.getSettings().setLastSaveFilePath(it) }
			val saveDirs: List<Path> = fileDialog.show()
			if (saveDirs.isEmpty()) {
				return@addActionListener
			}
			val path = saveDirs[0].toString()
			textField.text = path
		}
		return button
	}

	private fun exportProject() {
		val exportPathStr = exportProjectProperties.exportPath
		if (!validateAndMakeDir(exportPathStr)) {
			JOptionPane.showMessageDialog(
				this,
				NLS.str("message.enter_valid_path"),
				NLS.str("message.errorTitle"),
				JOptionPane.WARNING_MESSAGE,
			)
			return
		}
		mainWindow.getSettings().setLastSaveFilePath(Path.of(checkNotNull(exportPathStr)))
		LOG.debug("Export properties: {}", exportProjectProperties)
		exportListener(exportProjectProperties)
		dispose()
	}

	private fun validateAndMakeDir(exportPath: String?): Boolean {
		if (exportPath == null || exportPath.isBlank()) {
			return false
		}
		return try {
			val path = Path.of(exportPath)
			if (Files.isRegularFile(path)) {
				// 路径已经作为普通文件存在，不能作为目录导出
				return false
			}
			FileUtils.makeDirs(path)
			true
		} catch (e: Exception) {
			LOG.warn("Export path validate error, path string:{}", exportPath, e)
			false
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ExportProjectDialog::class.java)
	}
}
