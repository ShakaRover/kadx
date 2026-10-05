@file:Suppress("ktlint:standard:property-naming")

package kadx.api.plugins.events

import kadx.api.plugins.events.types.NodeRenamedByUser
import kadx.api.plugins.events.types.ReloadProject
import kadx.api.plugins.events.types.ReloadSettingsWindow

/**
 * 类型安全、可扩展的事件类型登记表。
 *
 * **做什么**：集中定义 kadx 内置的事件类型常量。插件与 GUI 通过
 * `KadxEvents.NODE_RENAMED_BY_USER` 等方式引用。
 *
 * **为什么用 `object` + `@JvmField`**：原 Java 是 `public static final` 常量，
 * Java 调用方按静态字段访问；`object` + `@JvmField` 生成的正是同名静态字段，
 * 保证 Java 侧零改动。
 */
object KadxEvents {

	/**
	 * 用户完成重命名后发出（仅 GUI）。
	 */
	@JvmField
	val NODE_RENAMED_BY_USER: KadxEventType<NodeRenamedByUser> = KadxEventType.create("NODE_RENAMED_BY_USER")

	/**
	 * 请求重新加载当前项目（仅 GUI）。
	 */
	@JvmField
	val RELOAD_PROJECT: KadxEventType<ReloadProject> = KadxEventType.create("RELOAD_PROJECT")

	/**
	 * 请求重新加载设置窗口（仅 GUI）。
	 * 适用于重载通过 `KadxGuiSettings.setCustomSettingsGroup` 注册的自定义设置组。
	 */
	@JvmField
	val RELOAD_SETTINGS_WINDOW: KadxEventType<ReloadSettingsWindow> = KadxEventType.create("RELOAD_SETTINGS_WINDOW")
}
