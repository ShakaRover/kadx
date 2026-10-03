package jadx.gui.settings.ui.cache

import jadx.api.plugins.gui.ISettingsGroup
import jadx.gui.cache.code.CodeCacheMode
import jadx.gui.cache.usage.UsageCacheMode
import jadx.gui.settings.ui.JadxSettingsWindow
import jadx.gui.settings.ui.SettingsGroup
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.utils.NLS
import jadx.gui.utils.files.JadxFiles
import jadx.gui.utils.ui.DocumentUpdateListener
import java.awt.BorderLayout
import java.awt.GridLayout
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.UIManager

/**
 * 缓存设置页：缓存模式选择、缓存目录位置选择，以及缓存条目的查看/清理表格。
 */
class CacheSettingsGroup(private val settingsWindow: JadxSettingsWindow) : ISettingsGroup {

	private val title: String = NLS.str("preferences.cache")

	private lateinit var customDirField: JTextField
	private lateinit var selectDirBtn: JButton

	override fun getTitle(): String = title

	override fun buildComponent(): JComponent {
		val options = JPanel()
		options.layout = BoxLayout(options, BoxLayout.PAGE_AXIS)
		options.add(buildBaseOptions())
		options.add(buildLocationSelector())

		val mainPanel = JPanel()
		mainPanel.layout = BorderLayout()
		mainPanel.add(options, BorderLayout.PAGE_START)
		mainPanel.add(buildCachesView(), BorderLayout.CENTER)
		return mainPanel
	}

	private fun buildCachesView(): JPanel {
		val cachesTable = CachesTable(settingsWindow.getMainWindow())
		val scrollPane = JScrollPane(cachesTable)
		cachesTable.setFillsViewportHeight(true)
		cachesTable.updateData()

		val calcUsage = JButton(NLS.str("preferences.cache.btn.usage"))
		calcUsage.addActionListener { cachesTable.updateSizes() }

		val deleteSelected = JButton(NLS.str("preferences.cache.btn.delete_selected"))
		deleteSelected.addActionListener { cachesTable.deleteSelected() }

		val deleteAll = JButton(NLS.str("preferences.cache.btn.delete_all"))
		deleteAll.addActionListener { cachesTable.deleteAll() }

		val buttons = JPanel()
		buttons.layout = BoxLayout(buttons, BoxLayout.LINE_AXIS)
		buttons.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
		buttons.add(calcUsage)
		buttons.add(Box.createHorizontalGlue())
		buttons.add(deleteSelected)
		buttons.add(deleteAll)

		val panel = JPanel()
		panel.layout = BorderLayout()
		panel.border = BorderFactory.createTitledBorder(NLS.str("preferences.cache.table.title"))
		panel.add(scrollPane, BorderLayout.CENTER)
		panel.add(buttons, BorderLayout.PAGE_END)
		return panel
	}

	private fun buildLocationSelector(): JComponent {
		val panel = JPanel()
		panel.layout = GridLayout(0, 1)
		panel.border = BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder(NLS.str("preferences.cache.location")),
			BorderFactory.createEmptyBorder(10, 10, 10, 10),
		)

		customDirField = JTextField()
		customDirField.columns = 10
		customDirField.document.addDocumentListener(
			DocumentUpdateListener {
				settingsWindow.getMainWindow().getSettings().setCacheDir(customDirField.text)
			},
		)

		selectDirBtn = JButton()
		selectDirBtn.icon = UIManager.getIcon("Tree.closedIcon")
		selectDirBtn.addActionListener {
			val fd = FileDialogWrapper(settingsWindow.getMainWindow(), FileOpenMode.CUSTOM_OPEN)
			fd.setFileExtList(emptyList())
			fd.setSelectionMode(JFileChooser.DIRECTORIES_ONLY)
			val paths: List<Path> = fd.show()
			if (paths.isNotEmpty()) {
				val dir = paths[0].toAbsolutePath().toString()
				customDirField.text = dir
				settingsWindow.getMainWindow().getSettings().setCacheDir(dir)
			}
		}

		val defOpt = JRadioButton(NLS.str("preferences.cache.location_default"))
		defOpt.toolTipText = JadxFiles.CACHE_DIR.toString()
		defOpt.addActionListener { changeCacheLocation(null) }
		val localOpt = JRadioButton(NLS.str("preferences.cache.location_local"))
		localOpt.addActionListener { changeCacheLocation(".") }
		val customOpt = JRadioButton(NLS.str("preferences.cache.location_custom"))
		customOpt.addActionListener { changeCacheLocation("") }

		val group = ButtonGroup()
		group.add(defOpt)
		group.add(localOpt)
		group.add(customOpt)

		panel.add(defOpt)
		panel.add(localOpt)

		val custom = JPanel()
		custom.layout = BoxLayout(custom, BoxLayout.LINE_AXIS)
		custom.add(customOpt)
		custom.add(Box.createHorizontalStrut(15))
		custom.add(customDirField)
		custom.add(selectDirBtn)
		panel.add(custom)

		val cacheDir = settingsWindow.getMainWindow().getSettings().getCacheDir()
		if (cacheDir == null) {
			defOpt.isSelected = true
			changeCacheLocation(null)
		} else if (cacheDir == ".") {
			localOpt.isSelected = true
			changeCacheLocation(cacheDir)
		} else {
			customOpt.isSelected = true
			customDirField.text = cacheDir
			changeCacheLocation("")
		}
		val notice = JLabel(NLS.str("preferences.cache.change_notice"))
		notice.isEnabled = false
		panel.add(notice)
		return panel
	}

	private fun changeCacheLocation(locValue: String?) {
		val custom = locValue == ""
		customDirField.isEnabled = custom
		selectDirBtn.isEnabled = custom
		if (!custom) {
			settingsWindow.getMainWindow().getSettings().setCacheDir(locValue)
		}
	}

	private fun buildBaseOptions(): JComponent {
		val settings = settingsWindow.getMainWindow().getSettings()

		val codeCacheModeComboBox = JComboBox(CodeCacheMode.values())
		codeCacheModeComboBox.selectedItem = settings.getCodeCacheMode()
		codeCacheModeComboBox.addActionListener {
			settings.setCodeCacheMode(codeCacheModeComboBox.selectedItem as CodeCacheMode)
			settingsWindow.needReload()
		}

		val usageCacheModeComboBox = JComboBox(UsageCacheMode.values())
		usageCacheModeComboBox.selectedItem = settings.getUsageCacheMode()
		usageCacheModeComboBox.addActionListener {
			settings.setUsageCacheMode(usageCacheModeComboBox.selectedItem as UsageCacheMode)
			settingsWindow.needReload()
		}

		val group = SettingsGroup(title)
		group.addRow(NLS.str("preferences.codeCacheMode"), CodeCacheMode.buildToolTip(), codeCacheModeComboBox)
		group.addRow(NLS.str("preferences.usageCacheMode"), usageCacheModeComboBox)
		return group.buildComponent()
	}
}
