package kadx.core.utils.exceptions

/**
 * 数值/寄存器索引溢出时抛出的运行时异常。
 *
 * **Kotlin 转换说明**：保留单个 `String` 构造器，继承 [KadxRuntimeException]，
 * 并用 `const val` 保留 [serialVersionUID]。
 */
class KadxOverflowException(message: String?) : KadxRuntimeException(message) {

	companion object {
		private const val serialVersionUID: Long = 2568659798680154204L
	}
}
