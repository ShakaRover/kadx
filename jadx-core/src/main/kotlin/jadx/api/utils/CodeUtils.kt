package jadx.api.utils

import jadx.api.ICodeInfo
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.core.dex.nodes.MethodNode
import java.util.function.BiFunction

/**
 * 反编译代码文本的定位工具：按字符偏移求行首/行尾/行号，以及从类代码中截取方法代码。
 *
 * **做什么**：
 * - `getLineForPos` / `getLineStartForPos` / `getLineEndForPos`：把字符偏移换算成行；
 * - `getNewLinePosBefore` / `getNewLinePosAfter`：查找前/后一个换行符（兼容 `\r\n`）；
 * - `getLineNumForPos`：按指定换行串计算第几行；
 * - `extractMethodCode` / `getMethodEnd`：借助代码元数据（[ICodeAnnotation]）截取方法代码。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 是纯静态工具类，Java 调用方写作
 * `CodeUtils.getLineForPos(...)`；Kotlin 侧也可 `import jadx.api.utils.CodeUtils.getLineForPos`。
 */
object CodeUtils {

	/** 返回 [pos] 所在行的文本（不含行尾换行符）。 */
	@JvmStatic
	fun getLineForPos(code: String, pos: Int): String {
		val start = getLineStartForPos(code, pos)
		val end = getLineEndForPos(code, pos)
		return code.substring(start, end)
	}

	/** 返回 [pos] 所在行的起始偏移；找不到前一个换行时返回 0。 */
	@JvmStatic
	fun getLineStartForPos(code: String, pos: Int): Int {
		val start = getNewLinePosBefore(code, pos)
		return if (start == -1) 0 else start + 1
	}

	/** 返回 [pos] 所在行的结束偏移；找不到后一个换行时返回文本长度。 */
	@JvmStatic
	fun getLineEndForPos(code: String, pos: Int): Int {
		val end = getNewLinePosAfter(code, pos)
		return if (end == -1) code.length else end
	}

	/** 返回 [startPos] 之后第一个换行符位置；若是 `\r\n`，定位到 `\r` 之前。找不到返回 -1。 */
	@JvmStatic
	fun getNewLinePosAfter(code: String, startPos: Int): Int {
		val pos = code.indexOf('\n', startPos)
		if (pos != -1) {
			// 检查 '\r\n' 组合：行尾应到 '\r' 之前
			val prev = pos - 1
			if (code[prev] == '\r') {
				return prev
			}
		}
		return pos
	}

	/** 返回 [startPos] 之前最后一个换行符位置；找不到返回 -1。 */
	@JvmStatic
	fun getNewLinePosBefore(code: String, startPos: Int): Int = code.lastIndexOf('\n', startPos)

	/** 按换行串 [newLine] 计算 [pos] 所在的行号（从 1 开始）。 */
	@JvmStatic
	fun getLineNumForPos(code: String, pos: Int, newLine: String): Int {
		val newLineLen = newLine.length
		var line = 1
		var prev = 0
		while (true) {
			val next = code.indexOf(newLine, prev)
			if (next >= pos) {
				return line
			}
			prev = next + newLineLen
			line++
		}
	}

	/**
	 * 从类代码中截取方法代码（包含其注释与注解）。
	 *
	 * @return 方法代码；元数据不可用时返回空字符串
	 */
	@JvmStatic
	fun extractMethodCode(mth: MethodNode, codeInfo: ICodeInfo): String {
		val end = getMethodEnd(mth, codeInfo)
		if (end == -1) {
			return ""
		}
		val start = getMethodStart(mth, codeInfo)
		if (end < start) {
			return ""
		}
		return codeInfo.codeStr.substring(start, end)
	}

	/**
	 * 在方法定义之前查找第一个空行，以便把方法的注释与注解一并包含进来。
	 */
	private fun getMethodStart(mth: MethodNode, codeInfo: ICodeInfo): Int {
		val pos = mth.defPosition
		val newLineStr = mth.root().getArgs().codeNewLineStr
		val emptyLine = newLineStr + newLineStr
		val emptyLinePos = codeInfo.codeStr.lastIndexOf(emptyLine, pos)
		return if (emptyLinePos == -1) pos else emptyLinePos + emptyLine.length
	}

	/**
	 * 在给定的类代码信息中查找方法结束位置。
	 *
	 * 算法：从方法定义位置往下扫描，跳过嵌套的 `DECLARATION`（类 / 方法声明），
	 * 遇到第一个未配对的 `END` 注解时，即为本方法的结束位置。
	 *
	 * @return 结束偏移；元数据不可用时返回 -1
	 */
	@JvmStatic
	fun getMethodEnd(mth: MethodNode, codeInfo: ICodeInfo): Int {
		if (!codeInfo.hasMetadata()) {
			return -1
		}
		val end = codeInfo.codeMetadata.searchDown(
			mth.defPosition + 1,
			object : BiFunction<Int, ICodeAnnotation, Int?> {
				/** 当前处于第几层嵌套的类 / 方法声明中。 */
				var nested = 0

				override fun apply(pos: Int, ann: ICodeAnnotation): Int? {
					when (ann.annType) {
						ICodeAnnotation.AnnType.DECLARATION -> {
							val node: ICodeNodeRef = (ann as NodeDeclareRef).getNode()
							when (node.annType) {
								ICodeAnnotation.AnnType.CLASS, ICodeAnnotation.AnnType.METHOD -> nested++
								else -> {}
							}
						}

						ICodeAnnotation.AnnType.END -> {
							if (nested == 0) {
								return pos
							}
							nested--
						}

						else -> {}
					}
					return null
				}
			},
		)
		return end ?: -1
	}
}
