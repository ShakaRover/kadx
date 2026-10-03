package jadx.gui.utils.plugins

import jadx.api.plugins.gui.ISettingsGroup
import org.slf4j.LoggerFactory
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * 插件自定义设置组的包装器。
 *
 * **做什么**：把某个插件提供的 [ISettingsGroup] 包一层，对 [getTitle] /
 * [buildComponent] / [getSubGroups] / [close] 统一做异常兜底，
 * 避免某个插件的错误拖垮整个设置窗口。
 *
 * **为什么保留显式函数**：本类实现核心接口 [ISettingsGroup]（已 Kotlin 化），
 * 覆写方法名必须与接口一致（`getTitle` 等）。
 */
class SettingsGroupPluginWrap(private val pluginId: String, private val pluginSettingGroup: ISettingsGroup) : ISettingsGroup {

	override fun getTitle(): String = try {
		pluginSettingGroup.getTitle()
	} catch (t: Throwable) {
		LOG.warn("Failed to get settings group title for plugin: {}", pluginId, t)
		"<error>"
	}

	override fun buildComponent(): JComponent = try {
		pluginSettingGroup.buildComponent()
	} catch (t: Throwable) {
		LOG.warn("Failed to build settings group component for plugin: {}", pluginId, t)
		JLabel("<error>")
	}

	override fun getSubGroups(): List<ISettingsGroup> = try {
		pluginSettingGroup.getSubGroups()
	} catch (t: Throwable) {
		LOG.warn("Failed to get settings group sub-groups for plugin: {}", pluginId, t)
		emptyList()
	}

	override fun close(save: Boolean) {
		try {
			pluginSettingGroup.close(save)
		} catch (t: Throwable) {
			LOG.warn("Failed to close settings group for plugin: {}", pluginId, t)
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(SettingsGroupPluginWrap::class.java)
	}
}
