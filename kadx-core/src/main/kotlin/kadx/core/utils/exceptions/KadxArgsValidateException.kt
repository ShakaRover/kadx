package kadx.core.utils.exceptions

/**
 * 命令行/API 参数校验失败时抛出的运行时异常。
 *
 * **Kotlin 转换说明**：保留原来的 `String` 与 `String + Throwable` 两个构造器，
 * 并用 `const val` 保留 [serialVersionUID]。
 */
class KadxArgsValidateException : RuntimeException {

	constructor(message: String) : super(message)

	constructor(message: String, cause: Throwable?) : super(message, cause)

	companion object {
		private const val serialVersionUID: Long = -7457621776087311909L
	}
}
