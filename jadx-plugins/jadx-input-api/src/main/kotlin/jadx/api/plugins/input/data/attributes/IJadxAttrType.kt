package jadx.api.plugins.input.data.attributes

/**
 * JADX 属性类型的标记接口。
 *
 * **设计目的**：提供一个类型安全的标记接口，用于区分不同的属性类型。
 * `JadxAttrType`类实现此接口并作为具体的类型载体。
 *
 * @param T 属性实例的类类型（必须继承自 [IJadxAttribute]）
 */
public interface IJadxAttrType<out T : IJadxAttribute> {
	companion object {
		/**
		 * 创建一个匿名的属性类型实例。
		 *
		 * @param A 属性类类型
		 * @return 新的类型标记实例
		 */
		@JvmStatic
		public fun <A : IJadxAttribute> create(): IJadxAttrType<A> = object : IJadxAttrType<A> {}

		/**
		 * 创建一个带名称的属性类型实例。
		 *
		 * @param A 属性类类型
		 * @param name 属性名称（用于 toString() 输出，便于调试）
		 * @return 新的类型标记实例
		 */
		@JvmStatic
		public fun <A : IJadxAttribute> create(name: String): IJadxAttrType<A> = object : IJadxAttrType<A> {
			override fun toString(): String = name
		}
	}
}
