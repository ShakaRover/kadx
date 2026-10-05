package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.data.ICatch
import kadx.api.plugins.input.data.ITry

/**
 * try 块的默认实现：偏移区间 + catch 子句。
 *
 * @param startOffset try 区间起始指令偏移
 * @param endOffset try 区间结束指令偏移
 * @param catchHandler 关联的 [ICatch]（异常类型 → 处理指令映射）
 */
public class TryData(
	private val startOffsetValue: Int,
	private val endOffsetValue: Int,
	private val catchHandler: ICatch,
) : ITry {

	override val catch: ICatch get() = catchHandler

	override val startOffset: Int get() = startOffsetValue

	override val endOffset: Int get() = endOffsetValue

	override fun toString(): String = "Try{" + InputUtils.formatOffset(startOffsetValue) + " - " + InputUtils.formatOffset(endOffsetValue) + ": " + catchHandler + '}'
}
