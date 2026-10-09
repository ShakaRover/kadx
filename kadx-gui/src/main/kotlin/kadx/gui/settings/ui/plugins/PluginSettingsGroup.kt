package kadx.gui.settings.ui.plugins

import kadx.api.plugins.gui.ISettingsGroup
import kadx.gui.utils.NLS
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 插件设置顶层页。
 *
 * **做什么**：作为设置树里的「Plugins」节点，自身只显示一行提示，
 * 具体设置由各内置插件的子页（[PluginSettings.addOptions] 生成的设置组）提供。
 *
 * **为什么不再有插件列表**：改版后的 kadx 不再支持外部插件，插件全部随发行包内置，
 * 没有可安装/卸载/更新的条目，因此原先的「已安装 / 可用 / 内置」列表与详情面板一并移除。
 */
internal class PluginSettingsGroup : ISettingsGroup {

	private val subGroups: MutableList<ISettingsGroup> = ArrayList()

	override fun getTitle(): String = NLS.str("preferences.plugins")

	override fun buildComponent(): JComponent {
		val panel = JPanel()
		panel.layout = BorderLayout()
		panel.add(JLabel(NLS.str("preferences.select_plugin")), BorderLayout.NORTH)
		return panel
	}

	override fun getSubGroups(): List<ISettingsGroup> = subGroups

	fun addSubGroup(group: ISettingsGroup) {
		subGroups.add(group)
	}

	override fun close(save: Boolean) {
		subGroups.forEach { it.close(save) }
	}
}
