package kadx.core.utils.exceptions

/**
 * kadx 通用非受检异常基类。
 *
 * **用途**：表示不应发生或无需调用方显式处理的内部错误（数据损坏、状态非法等）。
 * 继承自 [RuntimeException]，因此不需要在方法签名里声明。
 *
 * **Kotlin 转换说明**：保留无参/消息/消息+原因 3 个构造器，并声明为 `open`，
 * 因为 [InvalidDataException]、[KadxOverflowException] 继承它。
 */
open class KadxRuntimeException : RuntimeException {

	constructor() : super()

	constructor(message: String?) : super(message)

	constructor(message: String, cause: Throwable?) : super(message, cause)

	companion object {
		private const val serialVersionUID: Long = -7410848445429898248L
	}
}
