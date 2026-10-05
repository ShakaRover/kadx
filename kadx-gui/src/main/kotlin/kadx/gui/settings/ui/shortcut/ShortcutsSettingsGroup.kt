package kadx.gui.settings.ui.shortcut

import kadx.api.plugins.gui.ISettingsGroup
import kadx.gui.settings.KadxSettings
import kadx.gui.settings.ui.KadxSettingsWindow
import kadx.gui.settings.ui.SettingsGroup
import kadx.gui.ui.action.ActionCategory
import kadx.gui.ui.action.ActionModel
import kadx.gui.utils.NLS
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 快捷键设置页：按动作分类生成子设置组，每组内每个动作一行 [ShortcutEdit]。
 */
class ShortcutsSettingsGroup(
	private val settingsWindow: KadxSettingsWindow,
	private val settings: KadxSettings,
) : ISettingsGroup {

	override fun getTitle(): String = NLS.str("preferences.shortcuts")

	override fun buildComponent(): JComponent {
		val panel = JPanel()
		panel.layout = BorderLayout()
		panel.add(JLabel(NLS.str("preferences.select_shortcuts")), BorderLayout.NORTH)
		return panel
	}

	override fun getSubGroups(): List<ISettingsGroup> = ActionCategory.values().map { makeShortcutsGroup(it) }

	private fun makeShortcutsGroup(category: ActionCategory): SettingsGroup {
		val group = SettingsGroup(category.getName())
		for (actionModel in ActionModel.select(category)) {
			val shortcut = settings.shortcuts.get(actionModel)
			val edit = ShortcutEdit(actionModel, settingsWindow, settings)
			edit.setShortcut(shortcut)
			group.addRow(actionModel.getName(), edit)
		}
		return group
	}
}
