package kadx.api.plugins.input.data

/**
 * 方法句柄的类型枚举。
 *
 * 在 Java/Dex 中，方法句柄（Method Handle）是一种引用方法或字段的方式，
 * 用于 invokedynamic、bootstrap methods 等高级特性。这个枚举分类了所有可能的句柄操作类型：
 *
 * **字段访问类**（isField() = true）:
 * - STATIC_PUT/GET：静态字段的写入/读取
 * - INSTANCE_PUT/GET：实例字段的写入/读取
 *
 * **方法调用类**（isField() = false）:
 * - INVOKE_STATIC：静态方法调用
 * - INVOKE_INSTANCE：虚方法调用（普通实例方法）
 * - INVOKE_DIRECT：直接调用（私有方法、父类方法、构造函数）
 * - INVOKE_CONSTRUCTOR：构造函数调用
 * - INVOKE_INTERFACE：接口方法调用
 */
public enum class MethodHandleType(
	/** 人类可读的描述，用于日志和调试 */
	public val description: String,
) {
	/** 静态字段写入句柄 */
	STATIC_PUT("Static field put"),

	/** 静态字段读取句柄 */
	STATIC_GET("Static field get"),

	/** 实例字段写入句柄 */
	INSTANCE_PUT("Instance field put"),

	/** 实例字段读取句柄 */
	INSTANCE_GET("Instance field get"),

	/** 静态方法调用句柄 */
	INVOKE_STATIC("Invoke static method"),

	/** 虚方法调用句柄（普通实例方法） */
	INVOKE_INSTANCE("Invoke instance method"),

	/** 直接调用句柄（私有/父类方法） */
	INVOKE_DIRECT("Invoke direct method"),

	/** 构造函数调用句柄 */
	INVOKE_CONSTRUCTOR("Invoke constructor"),

	/** 接口方法调用句柄 */
	INVOKE_INTERFACE("Invoke interface method"),
	;

	/**
	 * 判断此句柄类型是否为字段访问操作。
	 *
	 * @return true 如果是字段访问（PUT/GET），false 如果是方法调用（INVOKE_*）
	 */
	public val isField: Boolean get() = when (this) {
		STATIC_PUT, STATIC_GET, INSTANCE_PUT, INSTANCE_GET -> true
		else -> false
	}
}
