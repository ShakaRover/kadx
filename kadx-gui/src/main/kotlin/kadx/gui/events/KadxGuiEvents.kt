package kadx.gui.events

import kadx.api.plugins.events.KadxEventType
import kadx.gui.events.types.TreeUpdate

/**
 * kadx-gui 专有的事件类型登记表。
 *
 * **做什么**：集中定义 GUI 层事件常量，供插件与 GUI 内部通过
 * `KadxGuiEvents.TREE_UPDATE` 引用。
 *
 * **为什么用 `@JvmField`**：原 Java 是 `public static final` 常量，Java 调用方按静态字段访问；
 * `@JvmField` 生成的正是同名静态字段，保证 Java 侧零改动。
 */
class KadxGuiEvents {

	companion object {
		/** 类树结构需要刷新（如重命名后）时发出。 */
		val TREE_UPDATE: KadxEventType<TreeUpdate> = KadxEventType.create("TREE_UPDATE")
	}
}
