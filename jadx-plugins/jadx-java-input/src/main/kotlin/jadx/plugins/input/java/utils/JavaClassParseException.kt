package jadx.plugins.input.java.utils

/**
 * Class 文件解析异常。
 *
 * **做什么**：当 .class 字节码结构不符合 JVM 规范（常量池标签未知、描述符非法等）时抛出，
 * 由上层 [jadx.plugins.input.java.JavaInputPlugin] 捕获并转换为加载错误信息。
 *
 * **为什么继承 RuntimeException**：解析失败属于"数据损坏"而非编程错误，
 * 原 Java 用非受检异常避免在每个读取方法上声明 throws。
 */
class JavaClassParseException : RuntimeException {

	constructor(message: String, cause: Throwable) : super(message, cause)

	constructor(message: String) : super(message)

	companion object {
		// JVM 序列化版本号（与原 Java private static final 字段等价，companion const val 生成同名静态字段）
		private const val serialVersionUID = -8452845601753645491L
	}
}
