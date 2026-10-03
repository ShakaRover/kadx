package jadx.gui.plugins.context

import jadx.api.plugins.gui.ISettingsGroup
import jadx.api.plugins.gui.JadxGuiSettings
import jadx.api.plugins.options.OptionDescription
import jadx.gui.settings.ui.SubSettingsGroup
import jadx.gui.settings.ui.plugins.PluginSettings

/**
 * [JadxGuiSettings] 的 GUI 实现：把插件设置请求转发到设置窗口。
 *
 * **做什么**：[setCustomSettingsGroup] 记录插件自定义设置页；
 * [buildSettingsGroupForOptions] 根据插件选项列表生成一个标准设置组。
 *
 * **java.util.List 说明**：核心接口 [JadxGuiSettings] 显式声明了
 * `java.util.List` 参数（保证 Java 实现零改动），而 [PluginSettings.addOptions]
 * 接受 Kotlin `List`。二者在 Kotlin 类型系统中不兼容，故用 `java.util.ArrayList`
 * 中转（SOP 规则 6 的已验证做法）。
 */
class GuiSettingsContext(private val guiPluginContext: GuiPluginContext) : JadxGuiSettings {

	override fun setCustomSettingsGroup(group: ISettingsGroup) {
		guiPluginContext.setCustomSettings(group)
	}

	override fun buildSettingsGroupForOptions(title: String, options: java.util.List<OptionDescription>): ISettingsGroup {
		val mainWindow = guiPluginContext.getCommonContext().getMainWindow()
		val pluginsSettings = PluginSettings(mainWindow, mainWindow.getSettings())
		val settingsGroup = SubSettingsGroup(title)
		val optionsList = java.util.ArrayList<OptionDescription>()
		optionsList.addAll(options)
		pluginsSettings.addOptions(settingsGroup, optionsList)
		return settingsGroup
	}
}
