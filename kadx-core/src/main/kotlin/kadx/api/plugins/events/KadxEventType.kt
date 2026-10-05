package kadx.api.plugins.events

/**
 * 带类型参数的事件类型标识（可扩展的枚举替代品）。
 *
 * **做什么**：每个事件类型是一个 [KadxEventType] 实例，携带其事件类 `T`。
 * 通过伴生对象上的工厂方法创建匿名子类实例。
 *
 * **为什么用匿名对象**：原 Java 的 `create()` 返回 `new KadxEventType<>(){}`；
 * Kotlin 用 `object : KadxEventType<E>() {}` 保持语义完全一致。
 * 工厂方法加 `@JvmStatic`，Java 侧仍可 `import static ...KadxEventType.create`。
 */
abstract class KadxEventType<T : IKadxEvent> {

	companion object {
		/** 创建一个不带名称的事件类型（`toString` 使用默认实现）。 */
		@JvmStatic
		fun <E : IKadxEvent> create(): KadxEventType<E> = object : KadxEventType<E>() {}

		/** 创建一个带名称的事件类型，[toString] 返回该名称。 */
		@JvmStatic
		fun <E : IKadxEvent> create(name: String): KadxEventType<E> = object : KadxEventType<E>() {
			override fun toString(): String = name
		}
	}
}
