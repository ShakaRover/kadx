@file:Suppress("ktlint:standard:property-naming")

package jadx.api.plugins.events.types

import jadx.api.plugins.events.IJadxEvent
import jadx.api.plugins.events.JadxEventType
import jadx.api.plugins.events.JadxEvents

/**
 * 「重新加载项目」事件（单例）。
 *
 * **做什么**：作为无载荷的哨兵事件发送；GUI 收到后重新打开当前项目。
 *
 * **为什么用私有构造器 + `EVENT` 常量**：与原 Java 的单例语义一致，
 * Java 侧继续通过 `ReloadProject.EVENT` 访问（`@JvmField` 生成静态字段）。
 */
class ReloadProject private constructor() : IJadxEvent {

	override fun getType(): JadxEventType<ReloadProject> = JadxEvents.RELOAD_PROJECT

	override fun toString(): String = "RELOAD_PROJECT"

	companion object {
		@JvmField
		val EVENT: ReloadProject = ReloadProject()
	}
}
