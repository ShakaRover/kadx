@file:Suppress("ktlint:standard:property-naming")

package kadx.api.plugins.events.types

import kadx.api.plugins.events.IKadxEvent
import kadx.api.plugins.events.KadxEventType
import kadx.api.plugins.events.KadxEvents

/**
 * 「重新加载项目」事件（单例）。
 *
 * **做什么**：作为无载荷的哨兵事件发送；GUI 收到后重新打开当前项目。
 *
 * **为什么用私有构造器 + `EVENT` 常量**：与原 Java 的单例语义一致，
 * Java 侧继续通过 `ReloadProject.EVENT` 访问（`@JvmField` 生成静态字段）。
 */
class ReloadProject private constructor() : IKadxEvent {

	override fun getType(): KadxEventType<ReloadProject> = KadxEvents.RELOAD_PROJECT

	override fun toString(): String = "RELOAD_PROJECT"

	companion object {
		@JvmField
		val EVENT: ReloadProject = ReloadProject()
	}
}
