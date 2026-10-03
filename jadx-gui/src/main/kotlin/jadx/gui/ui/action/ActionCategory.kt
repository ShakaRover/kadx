package jadx.gui.ui.action

import jadx.gui.utils.NLS

/**
 * 动作分类（用于把菜单/快捷键动作按区域分组）。
 *
 * **做什么**：每个枚举常量对应界面上的一个动作区域，并在类加载时通过 [NLS]
 * 取出本地化显示名。
 *
 * **注意**：`java.lang.Enum` 已经有一个 `name` 属性（对应 `name()`），
 * 若在此处直接声明 `val name` 会与之冲突，因此改用私有属性 [actionName]
 * 并保留显式的 [getName]，Java 调用方 `category.getName()` 零改动。
 */
enum class ActionCategory(
	private val actionName: String,
) {
	/** 菜单与工具栏动作。 */
	MENU_TOOLBAR(NLS.str("action_category.menu_toolbar")),

	/** 代码区右键菜单动作。 */
	CODE_AREA(NLS.str("action_category.code_area")),

	/** 插件脚本动作。 */
	PLUGIN_SCRIPT(NLS.str("action_category.plugin_script")),

	/** 十六进制查看器菜单动作。 */
	HEX_VIEWER_MENU(NLS.str("action_category.hex_viewer")),
	;

	/** 返回该分类的本地化显示名。 */
	fun getName(): String = actionName
}
