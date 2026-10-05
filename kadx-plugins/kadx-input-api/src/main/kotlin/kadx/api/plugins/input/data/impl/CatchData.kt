package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.data.ICatch

/**
 * 异常捕获（catch）信息的默认实现。
 *
 * @param handlers 各异常类型对应的处理指令偏移数组
 * @param types 捕获的异常类型名数组，与 [handlers] 一一对应
 * @param allHandler catch-all 处理指令偏移；无 catch-all 时为 -1
 */
public class CatchData(
	private val handlersValue: IntArray,
	private val typesValue: Array<String>,
	private val allHandler: Int,
) : ICatch {

	override val handlers: IntArray get() = handlersValue

	override val types: Array<String> get() = typesValue

	override val catchAllHandler: Int get() = allHandler

	override fun toString(): String {
		val sb = StringBuilder("Catch:")
		for (i in typesValue.indices) {
			sb.append(' ').append(typesValue[i]).append("->").append(InputUtils.formatOffset(handlersValue[i]))
		}
		if (allHandler != -1) {
			sb.append(" all->").append(InputUtils.formatOffset(allHandler))
		}
		return sb.toString()
	}
}
