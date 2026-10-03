package jadx.gui.ui.codearea

import jadx.api.ICodeInfo
import org.fife.ui.rtextarea.LineNumberFormatter

/**
 * 行号格式化器：把“反编译输出行号”映射为“dex 调试源码行号”显示。
 *
 * **做什么**：当开启 DEBUG 行号模式时，左侧行号列不显示 1、2、3…，
 * 而是显示该行对应的原始 Java 源码行号（来自代码元数据的行映射）。
 * 这样反编译结果与原始源码行号可以对上，便于对照调试。
 *
 * **为什么缓存最大长度**：行号列的宽度需要提前知道最大位数，避免输入时抖动，
 * 所以在构造时一次性算好。
 */
class SourceLineFormatter(private val codeInfo: ICodeInfo) : LineNumberFormatter {
	private val maxLength: Int = calcMaxLength(codeInfo)

	override fun format(lineNumber: Int): String {
		val sourceLine = codeInfo.getCodeMetadata().getLineMapping()[lineNumber]
			?: return ""
		return sourceLine.toString()
	}

	override fun getMaxLength(maxLineNumber: Int): Int = maxLength

	companion object {
		/** 计算行号映射里最大的源码行号需要几位数字。 */
		private fun calcMaxLength(codeInfo: ICodeInfo): Int {
			val maxLine = codeInfo.getCodeMetadata().getLineMapping().values.maxOrNull() ?: 1
			return getNumberLength(maxLine)
		}

		/** 返回整数 [num] 的十进制位数（`num < 10` 时为 1）。 */
		fun getNumberLength(num: Int): Int = if (num < 10) 1 else 1 + Math.log10(num.toDouble()).toInt()
	}
}
