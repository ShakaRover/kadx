package kadx.gui.settings.ui.plugins

import ch.qos.logback.classic.Level
import kadx.api.plugins.events.types.ReloadProject
import kadx.api.plugins.gui.ISettingsGroup
import kadx.api.plugins.gui.KadxGuiContext
import kadx.api.plugins.options.OptionDescription
import kadx.api.plugins.options.OptionFlag
import kadx.api.plugins.options.OptionType
import kadx.core.plugins.AppContext
import kadx.core.plugins.PluginRuntime
import kadx.core.utils.Utils
import kadx.gui.logs.LogOptions
import kadx.gui.plugins.context.GuiPluginContext
import kadx.gui.settings.KadxSettings
import kadx.gui.settings.ui.SettingsGroup
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import kadx.gui.utils.plugins.CollectPlugins
import kadx.gui.utils.plugins.SettingsGroupPluginWrap
import kadx.gui.utils.ui.DocumentUpdateListener
import kadx.plugins.tools.KadxPluginsTools
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
class PluginSettings(private val mainWindow: MainWindow, private val settings: KadxSettings) {

	fun build(): ISettingsGroup {
		val collectedPlugins: List<PluginRuntime> = CollectPlugins(mainWindow).build()
		val pluginsGroup = PluginSettingsGroup(this, mainWindow, collectedPlugins)
		for (plugin in collectedPlugins) {
			val pluginGroup = addPluginGroup(plugin)
			if (pluginGroup != null) {
				pluginsGroup.getSubGroups().add(SettingsGroupPluginWrap(plugin.pluginId, pluginGroup))
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
				val metadata = KadxPluginsTools.instance.install(locationId)
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
			val success = KadxPluginsTools.instance.uninstall(pluginId)
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
			Runnable { KadxPluginsTools.instance.changeDisabledStatus(pluginId, disabled) },
		) { requestReload() }
	}

	internal fun updateAll() {
		mainWindow.getBackgroundExecutor().execute(NLS.str("preferences.plugins.task.updating")) {
			val updates = KadxPluginsTools.instance.updateAll()
			if (updates.isNotEmpty()) {
				LOG.info("Updates: {}\n  ", Utils.listToString(updates, "\n  "))
				requestReload()
			} else {
				LOG.info("No updates found")
			}
		}
	}

	private fun addPluginGroup(plugin: PluginRuntime): ISettingsGroup? {
		val appContext: AppContext? = plugin.appContext
		val guiContext: KadxGuiContext? = appContext?.getGuiContext()
		if (guiContext is GuiPluginContext) {
			val customSettingsGroup = guiContext.customSettingsGroup
			if (customSettingsGroup != null) {
				return customSettingsGroup
			}
		}
		val options = plugin.options ?: return null
		val optionsDescriptions = options.getOptionsDescriptions()
		if (optionsDescriptions.isEmpty()) {
			return null
		}
		val settingsGroup = SettingsGroup(plugin.pluginInfo.getName())
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
				val optionsMap = settings.pluginOptions as MutableMap<String, String>
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
