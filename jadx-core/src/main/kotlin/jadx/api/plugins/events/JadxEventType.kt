package jadx.api.plugins.events

/**
 * 带类型参数的事件类型标识（可扩展的枚举替代品）。
 *
 * **做什么**：每个事件类型是一个 [JadxEventType] 实例，携带其事件类 `T`。
 * 通过伴生对象上的工厂方法创建匿名子类实例。
 *
 * **为什么用匿名对象**：原 Java 的 `create()` 返回 `new JadxEventType<>(){}`；
 * Kotlin 用 `object : JadxEventType<E>() {}` 保持语义完全一致。
 * 工厂方法加 `@JvmStatic`，Java 侧仍可 `import static ...JadxEventType.create`。
 */
abstract class JadxEventType<T : IJadxEvent> {

	companion object {
		/** 创建一个不带名称的事件类型（`toString` 使用默认实现）。 */
		@JvmStatic
		fun <E : IJadxEvent> create(): JadxEventType<E> = object : JadxEventType<E>() {}

		/** 创建一个带名称的事件类型，[toString] 返回该名称。 */
		@JvmStatic
		fun <E : IJadxEvent> create(name: String): JadxEventType<E> = object : JadxEventType<E>() {
			override fun toString(): String = name
		}
	}
}
