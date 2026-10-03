package jadx.gui.settings.ui.plugins

import ch.qos.logback.classic.Level
import jadx.api.plugins.events.types.ReloadProject
import jadx.api.plugins.gui.ISettingsGroup
import jadx.api.plugins.gui.JadxGuiContext
import jadx.api.plugins.options.OptionDescription
import jadx.api.plugins.options.OptionFlag
import jadx.api.plugins.options.OptionType
import jadx.core.plugins.PluginContext
import jadx.core.utils.Utils
import jadx.gui.logs.LogOptions
import jadx.gui.plugins.context.GuiPluginContext
import jadx.gui.settings.JadxSettings
import jadx.gui.settings.ui.SettingsGroup
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.plugins.CloseablePlugins
import jadx.gui.utils.plugins.CollectPlugins
import jadx.gui.utils.plugins.SettingsGroupPluginWrap
import jadx.gui.utils.ui.DocumentUpdateListener
import jadx.plugins.tools.JadxPluginsTools
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.event.ItemEvent
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.JTextField

/**
 * 插件设置的业务逻辑：构建插件设置组、安装/卸载/禁用插件，以及为插件选项生成编辑器。
 *
 * **线程模型**：安装、卸载、更新等耗时操作统一交给 `BackgroundExecutor` 后台执行，
 * 完成后回到 UI 线程请求重载，保持原 Swing 线程模型。
 */
class PluginSettings(private val mainWindow: MainWindow, private val settings: JadxSettings) {

	fun build(): ISettingsGroup {
		val collectedPlugins: CloseablePlugins = CollectPlugins(mainWindow).build()
		val pluginsGroup = PluginSettingsGroup(this, mainWindow, collectedPlugins)
		for (context in collectedPlugins.getList()) {
			val pluginGroup = addPluginGroup(context)
			if (pluginGroup != null) {
				pluginsGroup.getSubGroups().add(SettingsGroupPluginWrap(context.getPluginId(), pluginGroup))
			}
		}
		return pluginsGroup
	}

	fun addPlugin() {
		InstallPluginDialog(mainWindow, this).isVisible = true
	}

	private fun requestReload() {
		mainWindow.events().send(ReloadProject.EVENT)
	}

	fun install(locationId: String) {
		mainWindow.getBackgroundExecutor().execute(NLS.str("preferences.plugins.task.installing")) {
			try {
				val metadata = JadxPluginsTools.getInstance().install(locationId)
				LOG.info("Plugin installed: {}", metadata)
				requestReload()
			} catch (e: Exception) {
				LOG.error("Plugin install failed", e)
				mainWindow.showLogViewer(LogOptions.forLevel(Level.ERROR))
			}
		}
	}

	fun uninstall(pluginId: String) {
		mainWindow.getBackgroundExecutor().execute(NLS.str("preferences.plugins.task.uninstalling")) {
			val success = JadxPluginsTools.getInstance().uninstall(pluginId)
			if (success) {
				LOG.info("Uninstall complete")
				requestReload()
			} else {
				LOG.warn("Uninstall failed")
			}
		}
	}

	fun changeDisableStatus(pluginId: String, disabled: Boolean) {
		mainWindow.getBackgroundExecutor().execute(
			NLS.str("preferences.plugins.task.status"),
			Runnable { JadxPluginsTools.getInstance().changeDisabledStatus(pluginId, disabled) },
		) { requestReload() }
	}

	internal fun updateAll() {
		mainWindow.getBackgroundExecutor().execute(NLS.str("preferences.plugins.task.updating")) {
			val updates = JadxPluginsTools.getInstance().updateAll()
			if (updates.isNotEmpty()) {
				LOG.info("Updates: {}\n  ", Utils.listToString(updates, "\n  "))
				requestReload()
			} else {
				LOG.info("No updates found")
			}
		}
	}

	private fun addPluginGroup(context: PluginContext): ISettingsGroup? {
		val guiContext: JadxGuiContext? = context.getGuiContext()
		if (guiContext is GuiPluginContext) {
			val customSettingsGroup = guiContext.getCustomSettingsGroup()
			if (customSettingsGroup != null) {
				return customSettingsGroup
			}
		}
		val options = context.getOptions() ?: return null
		val optionsDescriptions = options.getOptionsDescriptions()
		if (optionsDescriptions.isEmpty()) {
			return null
		}
		val settingsGroup = SettingsGroup(context.getPluginInfo().getName())
		addOptions(settingsGroup, optionsDescriptions)
		return settingsGroup
	}

	fun addOptions(pluginGroup: SettingsGroup, optionsDescriptions: List<OptionDescription>) {
		for (opt in optionsDescriptions) {
			if (opt.getFlags().contains(OptionFlag.HIDE_IN_GUI)) {
				continue
			}
			val optName = opt.name()
			val title = opt.description()
			val updateFunc: (String) -> Unit
			val curValue: String?
			if (opt.getFlags().contains(OptionFlag.PER_PROJECT)) {
				val project = mainWindow.getProject()
				updateFunc = { value -> project.updatePluginOptions { m -> m[optName] = value } }
				curValue = project.getPluginOption(optName)
			} else {
				@Suppress("UNCHECKED_CAST")
				val optionsMap = settings.getPluginOptions() as MutableMap<String, String>
				updateFunc = { value -> optionsMap[optName] = value }
				curValue = optionsMap[optName]
			}
			val value = curValue ?: opt.defaultValue()

			var editor: JComponent? = null
			if (opt.values().isEmpty() || opt.getType() == OptionType.BOOLEAN) {
				try {
					editor = getPluginOptionEditor(opt, value, updateFunc)
				} catch (e: Exception) {
					LOG.error("Failed to add editor for plugin option: {}", optName, e)
				}
			} else {
				val combo = JComboBox(opt.values().toTypedArray())
				combo.selectedItem = value
				combo.addActionListener { updateFunc(combo.selectedItem as String) }
				editor = combo
			}
			if (editor != null) {
				val label = pluginGroup.addRow(title, editor)
				val enabled = !opt.getFlags().contains(OptionFlag.DISABLE_IN_GUI)
				if (!enabled) {
					label.isEnabled = false
					editor.isEnabled = false
				}
			}
		}
	}

	private fun getPluginOptionEditor(
		opt: OptionDescription,
		value: String?,
		updateFunc: (String) -> Unit,
	): JComponent? {
		when (opt.getType()) {
			OptionType.STRING -> {
				val textField = JTextField()
				textField.text = value ?: ""
				textField.document.addDocumentListener(
					DocumentUpdateListener { updateFunc(textField.text) },
				)
				return textField
			}

			OptionType.NUMBER -> {
				val numberField = JSpinner()
				numberField.value = safeStringToInt(value) {
					safeStringToInt(opt.defaultValue()) {
						throw IllegalArgumentException("Failed to parse integer default value: " + opt.defaultValue())
					}
				}
				numberField.addChangeListener { updateFunc(numberField.value.toString()) }
				return numberField
			}

			OptionType.BOOLEAN -> {
				val boolField = JCheckBox()
				boolField.isSelected = value == "yes" || value == "true"
				boolField.addItemListener { e ->
					val editorValue = e.stateChange == ItemEvent.SELECTED
					updateFunc(if (editorValue) "yes" else "no")
				}
				return boolField
			}
		}
		return null
	}

	private fun safeStringToInt(value: String?, defValueSupplier: () -> Int): Int {
		if (value == null) {
			return defValueSupplier()
		}
		try {
			return Integer.parseInt(value)
		} catch (e: Exception) {
			LOG.warn("Failed parse string to int: {}", value, e)
			return defValueSupplier()
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(PluginSettings::class.java)
	}
}
