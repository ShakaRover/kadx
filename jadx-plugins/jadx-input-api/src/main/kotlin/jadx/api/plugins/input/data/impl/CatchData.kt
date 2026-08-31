package jadx.api.plugins.input.data.impl

import jadx.api.plugins.input.data.ICatch

/**
 * 异常捕获（catch）信息的默认实现。
 *
 * @param handlers 各异常类型对应的处理指令偏移数组
 * @param types 捕获的异常类型名数组，与 [handlers] 一一对应
 * @param allHandler catch-all 处理指令偏移；无 catch-all 时为 -1
 */
public class CatchData(
	private val handlers: IntArray,
	private val types: Array<String>,
	private val allHandler: Int,
) : ICatch {

	override fun getHandlers(): IntArray = handlers

	override fun getTypes(): Array<String> = types

	override fun getCatchAllHandler(): Int = allHandler

	override fun toString(): String {
		val sb = StringBuilder("Catch:")
		for (i in types.indices) {
			sb.append(' ').append(types[i]).append("->").append(InputUtils.formatOffset(handlers[i]))
		}
		if (allHandler != -1) {
			sb.append(" all->").append(InputUtils.formatOffset(allHandler))
		}
		return sb.toString()
	}
}
