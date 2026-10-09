package kadx.gui.settings.ui.plugins

import kadx.api.plugins.gui.ISettingsGroup
import kadx.api.plugins.gui.KadxGuiContext
import kadx.api.plugins.options.OptionDescription
import kadx.api.plugins.options.OptionFlag
import kadx.api.plugins.options.OptionType
import kadx.core.plugins.AppContext
import kadx.core.plugins.PluginRuntime
import kadx.gui.plugins.context.GuiPluginContext
import kadx.gui.settings.KadxSettings
import kadx.gui.settings.ui.SettingsGroup
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import kadx.gui.utils.plugins.CollectPlugins
import kadx.gui.utils.plugins.SettingsGroupPluginWrap
import kadx.gui.utils.ui.DocumentUpdateListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.event.ItemEvent
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.JTextField

/**
 * 插件设置：为每个内置插件生成选项编辑页。
 *
 * **做什么**：[build] 收集当前加载的插件，为每个带选项（或自带自定义设置页）的插件
 * 生成一个子设置组；[addOptions] 把插件的选项描述渲染成「标签 + 编辑器」行。
 *
 * **为什么没有安装/卸载/更新**：改版后的 kadx 不再支持外部插件，插件全部随发行包内置，
 * 因此这里只保留设置界面，不涉及任何下载或文件操作。
 */
class PluginSettings(private val mainWindow: MainWindow, private val settings: KadxSettings) {

	fun build(): ISettingsGroup {
		val collectedPlugins: List<PluginRuntime> = CollectPlugins(mainWindow).build()
		val pluginsGroup = PluginSettingsGroup()
		for (plugin in collectedPlugins) {
			val pluginGroup = addPluginGroup(plugin)
			if (pluginGroup != null) {
				pluginsGroup.addSubGroup(SettingsGroupPluginWrap(plugin.pluginId, pluginGroup))
			}
		}
		return pluginsGroup
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
