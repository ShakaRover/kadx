package kadx.core.plugins.versions

/**
 * 版本号比较工具。
 *
 * **做什么**：把 kadx 版本字符串（如 `1.5.1`、`r3001.417bb7a`、`kadx-gui-1.5.1`）
 * 清洗为纯数字点分形式后逐段比较。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 类只有静态方法，Java 调用方
 * （测试、kadx-gui 的 `KadxUpdate`）仍写 `VersionComparator.checkAndCompare(...)`。
 *
 * **转换注意**：原 Java 用 `split("\\.")`（正则），Kotlin 的 `split("...")`
 * 是字面量切分，因此这里显式使用 `Regex("\\.")`，保证语义一致。
 */
object VersionComparator {

	/** 清洗后比较两个版本字符串，返回负数 / 0 / 正数。 */
	fun checkAndCompare(str1: String?, str2: String?): Int = compare(clean(str1), clean(str2))

	/** 去掉版本前缀（`kadx-gui-`、`kadx-`、`v`、`r`）与不稳定版本后缀。 */
	private fun clean(str: String?): String {
		if (str == null || str.isEmpty()) {
			return ""
		}
		var result = str.trim().lowercase()
		if (result.startsWith("kadx-gui-")) {
			result = result.substring(9)
		}
		if (result.startsWith("kadx-")) {
			result = result.substring(5)
		}
		if (result[0] == 'v') {
			result = result.substring(1)
		}
		if (result[0] == 'r') {
			result = result.substring(1)
			val dot = result.indexOf('.')
			if (dot != -1) {
				result = result.substring(0, dot)
			}
		}
		// 把包版本后缀也当作版本的一部分
		result = result.replace('-', '.')
		return result
	}

	/** 按点分段比较：先跳过相同段，再比较第一个不同的数字段。 */
	private fun compare(str1: String, str2: String): Int {
		val s1 = str1.split(Regex("\\."))
		val l1 = s1.size
		val s2 = str2.split(Regex("\\."))
		val l2 = s2.size

		var i = 0
		// 跳过相同段
		while (i < l1 && i < l2) {
			if (s1[i] != s2[i]) {
				break
			}
			i++
		}
		// 比较第一个不同的数字段
		if (i < l1 && i < l2) {
			return Integer.valueOf(s1[i]).compareTo(Integer.valueOf(s2[i]))
		}
		val checkFirst = l1 > l2
		val zeroTail = isZeroTail(if (checkFirst) s1 else s2, i)
		if (zeroTail) {
			return 0
		}
		return if (checkFirst) 1 else -1
	}

	/** 判断从 pos 开始到末尾的段是否全为 0（用于把 `0.5` 与 `0.5.0` 视为相等）。 */
	private fun isZeroTail(arr: List<String>, pos: Int): Boolean {
		for (i in pos until arr.size) {
			if (Integer.parseInt(arr[i]) != 0) {
				return false
			}
		}
		return true
	}
}
