@file:Suppress("ktlint:standard:property-naming")

package kadx.api.plugins.events.types

import kadx.api.plugins.events.IKadxEvent
import kadx.api.plugins.events.KadxEventType
import kadx.api.plugins.events.KadxEvents

/**
 * 「重新加载设置窗口」事件（单例）。
 *
 * **做什么**：作为无载荷的哨兵事件发送；设置窗口收到后重建自定义设置组页面。
 *
 * **为什么用私有构造器 + `INSTANCE` 常量**：与原 Java 的单例语义一致，
 * Java 侧继续通过 `ReloadSettingsWindow.INSTANCE` 访问（`@JvmField` 生成静态字段）。
 */
class ReloadSettingsWindow private constructor() : IKadxEvent {

	override fun getType(): KadxEventType<ReloadSettingsWindow> = KadxEvents.RELOAD_SETTINGS_WINDOW

	override fun toString(): String = "RELOAD_SETTINGS_WINDOW"

	companion object {
		@JvmField
		val INSTANCE: ReloadSettingsWindow = ReloadSettingsWindow()
	}
}
