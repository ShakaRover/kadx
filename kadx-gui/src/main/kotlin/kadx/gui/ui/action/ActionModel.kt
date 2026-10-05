package kadx.gui.ui.action

import kadx.core.utils.Utils
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.gui.utils.shortcut.Shortcut
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.ImageIcon

/**
 * 全部 GUI 动作的登记表（枚举）。
 *
 * **做什么**：每个枚举常量描述一个动作：所属分类、本地化名称、描述、图标与默认快捷键。
 * 菜单、工具栏与代码区右键菜单都从这里取元数据，[IShortcutAction] 实现类也据此绑定快捷键。
 *
 * **为什么不用 `data class`**：枚举天然单例，且此处需要保留“按常量身份”比较。
 *
 * **注意**：`java.lang.Enum` 已有 `name` 属性（对应 `name()`），若直接声明 `val name`
 * 会冲突；因此本地化名称存于私有属性 [actionName]，并保留显式 [getName]，
 * 以保证 Java 调用方 `actionModel.getName()` / `actionModel.name()` 语义不变。
 */
enum class ActionModel(
	/** 动作所属分类。 */
	val category: ActionCategory,
	/** 本地化显示名。 */
	private val actionName: String,
	/** 描述文本（可为 null，为 null 时回退到显示名）。 */
	desc: String?,
	/** 图标资源路径（可为 null）。 */
	iconPath: String?,
	/** 默认快捷键（可为 null，为 null 时表示无快捷键）。 */
	defaultShortcut: Shortcut?,
) {
	ABOUT(ActionCategory.MENU_TOOLBAR, NLS.str("menu.about"), null, "ui/showInfos", Shortcut.keyboard(KeyEvent.VK_F1)),
	OPEN(ActionCategory.MENU_TOOLBAR, NLS.str("file.open_action"), null, "ui/openDisk", Shortcut.keyboard(KeyEvent.VK_O, UiUtils.ctrlButton())),
	OPEN_PROJECT(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("file.open_project"),
		null,
		"ui/projectDirectory",
		Shortcut.keyboard(KeyEvent.VK_O, InputEvent.SHIFT_DOWN_MASK or UiUtils.ctrlButton()),
	),
	ADD_FILES(ActionCategory.MENU_TOOLBAR, NLS.str("file.add_files_action"), null, "ui/addFile", null),
	NEW_PROJECT(ActionCategory.MENU_TOOLBAR, NLS.str("file.new_project"), null, "ui/newFolder", null),
	SAVE_PROJECT(ActionCategory.MENU_TOOLBAR, NLS.str("file.save_project"), null, null, null),
	SAVE_PROJECT_AS(ActionCategory.MENU_TOOLBAR, NLS.str("file.save_project_as"), null, null, null),
	RELOAD(ActionCategory.MENU_TOOLBAR, NLS.str("file.reload"), null, "ui/refresh", Shortcut.keyboard(KeyEvent.VK_F5)),
	LIVE_RELOAD(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("file.live_reload"),
		NLS.str("file.live_reload_desc"),
		null,
		Shortcut.keyboard(KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK),
	),
	SAVE_ALL(ActionCategory.MENU_TOOLBAR, NLS.str("file.save_all"), null, "ui/menu-saveall", Shortcut.keyboard(KeyEvent.VK_E, UiUtils.ctrlButton())),
	EXPORT(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("file.export"),
		null,
		"ui/export",
		Shortcut.keyboard(KeyEvent.VK_E, UiUtils.ctrlButton() or InputEvent.SHIFT_DOWN_MASK),
	),
	PREFS(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("menu.preferences"),
		null,
		"ui/settings",
		Shortcut.keyboard(KeyEvent.VK_P, UiUtils.ctrlButton() or InputEvent.SHIFT_DOWN_MASK),
	),
	EXIT(ActionCategory.MENU_TOOLBAR, NLS.str("file.exit"), null, "ui/exit", null),
	SYNC(ActionCategory.MENU_TOOLBAR, NLS.str("menu.sync"), null, "ui/locate", Shortcut.keyboard(KeyEvent.VK_T, UiUtils.ctrlButton())),
	TEXT_SEARCH(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("menu.text_search"),
		null,
		"ui/find",
		Shortcut.keyboard(KeyEvent.VK_F, UiUtils.ctrlButton() or InputEvent.SHIFT_DOWN_MASK),
	),

	CLASS_SEARCH(ActionCategory.MENU_TOOLBAR, NLS.str("menu.class_search"), null, "ui/ejbFinderMethod", Shortcut.keyboard(KeyEvent.VK_N, UiUtils.ctrlButton())),
	COMMENT_SEARCH(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("menu.comment_search"),
		null,
		"ui/usagesFinder",
		Shortcut.keyboard(KeyEvent.VK_SEMICOLON, UiUtils.ctrlButton() or InputEvent.SHIFT_DOWN_MASK),
	),
	GO_TO_MAIN_ACTIVITY(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("menu.go_to_main_activity"),
		null,
		"ui/home",
		Shortcut.keyboard(KeyEvent.VK_M, UiUtils.ctrlButton() or InputEvent.SHIFT_DOWN_MASK),
	),
	GO_TO_APPLICATION(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("menu.go_to_application"),
		null,
		"ui/application",
		Shortcut.keyboard(KeyEvent.VK_A, UiUtils.ctrlButton() or InputEvent.SHIFT_DOWN_MASK),
	),
	GO_TO_ANDROID_MANIFEST(ActionCategory.MENU_TOOLBAR, NLS.str("menu.go_to_android_manifest"), null, "ui/androidManifest", null),
	PREVIEW_TAB(ActionCategory.MENU_TOOLBAR, NLS.str("menu.enable_preview_tab"), null, "ui/editorPreview", null),
	DECOMPILE_ALL(ActionCategory.MENU_TOOLBAR, NLS.str("menu.decompile_all"), null, "ui/runAll", null),
	RESET_CACHE(ActionCategory.MENU_TOOLBAR, NLS.str("menu.reset_cache"), null, "ui/reset", null),
	DEOBF(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("menu.deobfuscation"),
		null,
		"ui/helmChartLock",
		Shortcut.keyboard(KeyEvent.VK_D, UiUtils.ctrlButton() or InputEvent.ALT_DOWN_MASK),
	),
	SHOW_LOG(
		ActionCategory.MENU_TOOLBAR,
		NLS.str("menu.log"),
		null,
		"ui/logVerbose",
		Shortcut.keyboard(KeyEvent.VK_L, UiUtils.ctrlButton() or InputEvent.SHIFT_DOWN_MASK),
	),
	CREATE_DESKTOP_ENTRY(ActionCategory.MENU_TOOLBAR, NLS.str("menu.create_desktop_entry"), null, null, null),
	BACK(ActionCategory.MENU_TOOLBAR, NLS.str("nav.back"), null, "ui/left", Shortcut.keyboard(KeyEvent.VK_ESCAPE)),
	BACK_V(ActionCategory.MENU_TOOLBAR, NLS.str("action.variant", NLS.str("nav.back")), null, "ui/left", null),
	FORWARD(ActionCategory.MENU_TOOLBAR, NLS.str("nav.forward"), null, "ui/right", Shortcut.keyboard(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK)),
	FORWARD_V(ActionCategory.MENU_TOOLBAR, NLS.str("action.variant", NLS.str("nav.forward")), null, "ui/right", null),
	QUARK(ActionCategory.MENU_TOOLBAR, NLS.str("menu.quark"), null, "ui/quark", null),
	OPEN_DEVICE(ActionCategory.MENU_TOOLBAR, NLS.str("debugger.process_selector"), null, "ui/startDebugger", null),

	FIND_USAGE(ActionCategory.CODE_AREA, NLS.str("popup.find_usage"), null, null, Shortcut.keyboard(KeyEvent.VK_X)),
	FIND_USAGE_PLUS(ActionCategory.CODE_AREA, NLS.str("popup.usage_dialog_plus"), null, null, Shortcut.keyboard(KeyEvent.VK_C)),
	GOTO_DECLARATION(ActionCategory.CODE_AREA, NLS.str("popup.go_to_declaration"), null, null, Shortcut.keyboard(KeyEvent.VK_D)),
	CONVERT_NUMBER(ActionCategory.CODE_AREA, NLS.str("popup.convert_number"), null, null, null),
	VIEW_CLASS_INHERITANCE_GRAPH(
		ActionCategory.CODE_AREA,
		NLS.str("popup.view_class_graph"),
		NLS.str("popup.view_class_graph_description"),
		null,
		null,
	),
	VIEW_CLASS_METHOD_GRAPH(
		ActionCategory.CODE_AREA,
		NLS.str("popup.view_class_method_graph"),
		NLS.str("popup.view_class_method_graph_description"),
		null,
		null,
	),
	VIEW_CALL_GRAPH(ActionCategory.CODE_AREA, NLS.str("popup.view_call_graph"), NLS.str("popup.view_call_graph_description"), null, null),
	VIEW_CONTROL_FLOW_GRAPH(ActionCategory.CODE_AREA, NLS.str("popup.view_cfg"), null, null, null),

	CODE_COMMENT(ActionCategory.CODE_AREA, NLS.str("popup.add_comment"), null, null, Shortcut.keyboard(KeyEvent.VK_SEMICOLON)),
	CODE_COMMENT_SEARCH(ActionCategory.CODE_AREA, NLS.str("popup.search_comment"), null, null, Shortcut.keyboard(KeyEvent.VK_SEMICOLON, UiUtils.ctrlButton())),
	CODE_RENAME(ActionCategory.CODE_AREA, NLS.str("popup.rename"), null, null, Shortcut.keyboard(KeyEvent.VK_N)),
	FRIDA_COPY(ActionCategory.CODE_AREA, NLS.str("popup.frida"), null, null, Shortcut.keyboard(KeyEvent.VK_F)),
	XPOSED_COPY(ActionCategory.CODE_AREA, NLS.str("popup.xposed"), null, null, Shortcut.keyboard(KeyEvent.VK_Y)),
	COPY_REFERENCE(ActionCategory.CODE_AREA, NLS.str("popup.copy_reference"), null, null, Shortcut.keyboard(KeyEvent.VK_R)),
	COPY_SMALI_REFERENCE(ActionCategory.CODE_AREA, NLS.str("popup.copy_smali_reference"), null, null, null),
	JSON_PRETTIFY(ActionCategory.CODE_AREA, NLS.str("popup.json_prettify"), null, null, null),

	SCRIPT_RUN(ActionCategory.PLUGIN_SCRIPT, NLS.str("script.run"), null, "ui/run", Shortcut.keyboard(KeyEvent.VK_F8)),
	SCRIPT_SAVE(ActionCategory.PLUGIN_SCRIPT, NLS.str("script.save"), null, "ui/menu-saveall", Shortcut.keyboard(KeyEvent.VK_S, UiUtils.ctrlButton())),
	SCRIPT_AUTO_COMPLETE(ActionCategory.PLUGIN_SCRIPT, NLS.str("script.auto_complete"), null, null, Shortcut.keyboard(KeyEvent.VK_SPACE, UiUtils.ctrlButton())),

	HEX_VIEWER_SHOW_INSPECTOR(ActionCategory.HEX_VIEWER_MENU, NLS.str("hex_viewer.show_inspector"), null, null, null),
	HEX_VIEWER_CHANGE_ENCODING(ActionCategory.HEX_VIEWER_MENU, NLS.str("hex_viewer.change_encoding"), null, null, null),
	HEX_VIEWER_GO_TO_ADDRESS(
		ActionCategory.HEX_VIEWER_MENU,
		NLS.str("hex_viewer.goto_address"),
		null,
		null,
		Shortcut.keyboard(KeyEvent.VK_J, UiUtils.ctrlButton()),
	),
	HEX_VIEWER_FIND(ActionCategory.HEX_VIEWER_MENU, NLS.str("hex_viewer.find"), null, null, Shortcut.keyboard(KeyEvent.VK_F, UiUtils.ctrlButton())),
	;

	/** 描述文本（为空时回退到 [actionName]）。 */
	private val description: String = Utils.getOrElse(desc, actionName)

	/** 图标（加载失败或未指定时为 null）。 */
	private val icon: ImageIcon? = if (iconPath != null) UiUtils.openSvgIcon(iconPath) else null

	/** 默认快捷键（未指定时用 [Shortcut.none]）。 */
	private val defaultShortcutValue: Shortcut = defaultShortcut ?: Shortcut.none()

	/** 返回本地化显示名。 */
	fun getName(): String = actionName

	/** 返回描述文本。 */
	fun getDescription(): String = description

	/** 返回图标（可为 null）。 */
	fun getIcon(): ImageIcon? = icon

	/** 返回默认快捷键。 */
	val defaultShortcut: Shortcut get() = defaultShortcutValue

	companion object {
		/** 取出属于指定分类的全部动作（保持枚举声明顺序）。 */
		fun select(category: ActionCategory): List<ActionModel> = values().filter { it.category == category }
	}
}
