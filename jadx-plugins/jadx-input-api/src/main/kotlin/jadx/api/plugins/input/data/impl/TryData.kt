package jadx.api.plugins.input.data.impl

import jadx.api.plugins.input.data.ICatch
import jadx.api.plugins.input.data.ITry

/**
 * try 块的默认实现：偏移区间 + catch 子句。
 *
 * @param startOffset try 区间起始指令偏移
 * @param endOffset try 区间结束指令偏移
 * @param catchHandler 关联的 [ICatch]（异常类型 → 处理指令映射）
 */
public class TryData(
	private val startOffset: Int,
	private val endOffset: Int,
	private val catchHandler: ICatch,
) : ITry {

	override fun getCatch(): ICatch = catchHandler

	override fun getStartOffset(): Int = startOffset

	override fun getEndOffset(): Int = endOffset

	override fun toString(): String = "Try{" + InputUtils.formatOffset(startOffset) + " - " + InputUtils.formatOffset(endOffset) + ": " + catchHandler + '}'
}
