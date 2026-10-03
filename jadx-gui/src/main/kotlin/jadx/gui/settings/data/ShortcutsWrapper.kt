package jadx.gui.settings.data

import jadx.gui.ui.action.ActionModel
import jadx.gui.utils.shortcut.Shortcut

/**
 * 快捷键映射的运行时包装器。
 *
 * **做什么**：内部持有 `ActionModel -> Shortcut` 的映射；[get] 在未自定义时回退到动作的默认快捷键，
 * [put] 用于用户修改快捷键后写入映射。
 *
 * **为什么字段可空/延迟初始化**：原 Java 字段初始为 `null`，由 `JadxSettings.loadSettingsData`
 * 通过 [updateShortcuts] 注入映射后才可用，这里用 `lateinit` 保持“未注入即报错”的语义。
 */
class ShortcutsWrapper {

	private lateinit var shortcuts: MutableMap<ActionModel, Shortcut>

	fun updateShortcuts(shortcuts: MutableMap<ActionModel, Shortcut>) {
		this.shortcuts = shortcuts
	}

	/** 获取动作的快捷键；未自定义时返回动作默认值。 */
	fun get(actionModel: ActionModel): Shortcut = shortcuts.getOrDefault(actionModel, actionModel.defaultShortcut)

	/** 写入（覆盖）某动作的快捷键。 */
	fun put(actionModel: ActionModel, shortcut: Shortcut) {
		shortcuts[actionModel] = shortcut
	}
}
