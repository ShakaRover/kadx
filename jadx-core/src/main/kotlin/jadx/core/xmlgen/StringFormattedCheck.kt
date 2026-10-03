package jadx.core.xmlgen

import java.util.Objects

/**
 * 字符串格式化占位符检查工具（来源：Apktool 的 ResXmlEncoders）。
 *
 * 用途：判断字符串是否包含“多个非位置参数占位符”（如 `%s`），
 * 若是则在生成 strings.xml 时标记 `formatted="false"`。
 */
object StringFormattedCheck {

	/** 是否包含多个非位置参数占位符（`%s` 之类，`%1$s` 视为位置参数）。 */
	fun hasMultipleNonPositionalSubstitutions(str: String): Boolean {
		val tuple = findSubstitutions(str, 4)
		return tuple.m1.isNotEmpty() && tuple.m1.size + tuple.m2.size > 1
	}

	/**
	 * 简单的二元组容器（保留原 Java 的自定义 equals/hashCode）。
	 * 解析产物，按引用标识使用，故为普通类而非 `data class`。
	 */
	@Suppress("checkstyle:ClassTypeParameterName")
	private class Duo<T1, T2>(val m1: T1, val m2: T2) {
		override fun equals(obj: Any?): Boolean {
			if (obj == null) {
				return false
			}
			if (javaClass != obj.javaClass) {
				return false
			}
			val other = obj as Duo<*, *>
			if (!Objects.equals(this.m1, other.m1)) {
				return false
			}
			return Objects.equals(this.m2, other.m2)
		}

		override fun hashCode(): Int {
			var hash = 3
			hash = 71 * hash + (this.m1?.hashCode() ?: 0)
			hash = 71 * hash + (this.m2?.hashCode() ?: 0)
			return hash
		}
	}

	/**
	 * 返回二元组：
	 * - m1：非位置参数占位符的偏移列表（`%` 后既不是 `%%` 也不是 `%\d+\$`）；
	 * - m2：位置参数占位符的偏移列表。
	 */
	private fun findSubstitutions(str: String, nonPosMaxRaw: Int): Duo<List<Int>, List<Int>> {
		var nonPosMax = nonPosMaxRaw
		if (nonPosMax == -1) {
			nonPosMax = Int.MAX_VALUE
		}
		var pos: Int
		var pos2 = 0
		val nonPositional = ArrayList<Int>()
		val positional = ArrayList<Int>()

		val length = str.length

		while (true) {
			pos = str.indexOf('%', pos2)
			if (pos == -1) {
				break
			}
			pos2 = pos + 1
			if (pos2 == length) {
				nonPositional.add(pos)
				break
			}
			var c = str[pos2++]
			if (c == '%') {
				continue
			}
			if (c in '0'..'9' && pos2 < length) {
				// 读取连续数字，判断是否以 '$' 结尾（位置参数 %1$s）
				do {
					c = str[pos2++]
				} while (c in '0'..'9' && pos2 < length)
				if (c == '$') {
					positional.add(pos)
					continue
				}
			}

			nonPositional.add(pos)
			if (nonPositional.size >= nonPosMax) {
				break
			}
		}

		return Duo(nonPositional, positional)
	}
}
