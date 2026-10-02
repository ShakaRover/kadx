package jadx.core.dex.attributes.nodes

import jadx.core.utils.Utils

/**
 * 反编译错误记录：一条错误描述 + 可选的异常原因。
 *
 * **相等性**：只比较错误字符串 [error]，因此同一错误（即使异常对象不同）会被视为重复，
 * 便于去重。实现 [Comparable] 是为了让错误列表输出时按字符串排序、结果稳定。
 *
 * **Kotlin 转换说明**：原 Java 用 `Objects.requireNonNull(error)`，Kotlin 的非空参数
 * 已经提供等价的 NPE 检查，故 [error] 直接用非空 `val`。
 */
class JadxError(
	val error: String,
	val cause: Throwable?,
) : Comparable<JadxError> {

	override fun compareTo(other: JadxError): Int = this.error.compareTo(other.error)

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val o = other as JadxError
		return error == o.error
	}

	override fun hashCode(): Int = error.hashCode()

	override fun toString(): String {
		val str = StringBuilder()
		str.append("JadxError: ").append(error).append(' ')
		if (cause != null) {
			str.append(cause.javaClass)
			str.append(':')
			str.append(cause.message)
			str.append('\n')
			str.append(Utils.getStackTrace(cause))
		}
		return str.toString()
	}
}
