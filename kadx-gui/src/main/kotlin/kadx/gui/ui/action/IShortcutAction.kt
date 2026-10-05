package kadx.gui.ui.action

import kadx.gui.utils.shortcut.Shortcut
import javax.swing.JComponent

/**
 * “可绑定快捷键的动作”接口。
 *
 * **做什么**：抽象出菜单动作与自动补全等可绑定快捷键的对象，[ShortcutsController]
 * 通过该接口统一设置/获取快捷键与触发动作。
 *
 * **为什么保留显式 `getXxx()` 函数**：该接口被 Java 与 Kotlin 同时实现/调用，
 * 显式函数保证 Java 侧零改动；参数/返回值可空性与原 Java 语义一致
 * （`getActionModel()`/`getShortcutComponent()` 可能为 null）。
 */
interface IShortcutAction {
	/** 返回动作对应的元数据；无元数据时为 null。 */
	fun getActionModel(): ActionModel?

	/** 返回用于绑定快捷键的 Swing 组件；未绑定时为 null。 */
	fun getShortcutComponent(): JComponent?

	/** 触发该动作。 */
	fun performAction()

	/** 设置快捷键；传 null 表示解绑。 */
	fun setShortcut(shortcut: Shortcut?)
}
