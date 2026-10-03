package jadx.plugins.input.java.data.code.trycatch

import jadx.api.plugins.input.data.ICatch
import jadx.api.plugins.input.data.ITry
import jadx.api.plugins.utils.Utils

/**
 * 一个 try 块（异常表按 start_pc 聚合后的形式）。
 *
 **做什么**：记录 try 块的字节码范围；[setCatch] 在解析完所有子句后填入合并的 [ICatch]。
 * equals/hashCode 只比较偏移区间，因为 [JavaCodeReader] 用它做"起始偏移 → try 块"的去重 map 键。
 */
class JavaTryData(
	private val startOffsetValue: Int,
	private val endOffsetValue: Int,
) : ITry {

	private var catchHandler: ICatch? = null

	override val catch: ICatch get() {
		// 接口声明非空；实际调用前 setCatch 必已执行（JavaCodeReader 聚合完子句才交给 core），
		// 若提前调用，原 Java 返回 null 后调用方解引用同样 NPE，行为等价
		return catchHandler ?: throw NullPointerException("catchHandler is not set")
	}

	fun setCatch(catchHandler: ICatch) {
		this.catchHandler = catchHandler
	}

	override val startOffset: Int get() = startOffsetValue

	override val endOffset: Int get() = endOffsetValue

	override fun hashCode(): Int = startOffsetValue + 31 * endOffsetValue

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o !is JavaTryData) {
			return false
		}
		val that = o
		return startOffsetValue == that.startOffset && endOffsetValue == that.endOffset
	}

	override fun toString(): String = "Try{" + Utils.formatOffset(startOffsetValue) + " - " + Utils.formatOffset(endOffsetValue) + ": " + catchHandler + '}'
}
