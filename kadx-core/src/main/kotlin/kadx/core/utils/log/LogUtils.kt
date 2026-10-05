package kadx.core.utils.log

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * 日志注入防护工具。
 *
 * **背景**：来自不可信输入（文件名、类名、资源字符串等）的内容如果直接写日志，
 * 攻击者可用换行/控制字符伪造日志条目（CodeQL 的 java-log-injection 告警）。
 * 这里把除“字母数字、下划线、点、冒号、分号、逗号、空格、减号”以外的字符替换为 `.`。
 *
 * **Kotlin 转换说明**：全部为静态方法，用 `object` + `@JvmStatic` 保持 Java 调用不变；
 * 两个 `escape` 重载的原 Java 实现都显式处理 null，故参数声明为可空。
 */
object LogUtils {

	/**
	 * 替换模式：保留 `\w`（字母数字下划线）、`.`、`:`、`;`、`,`、空格、`-`，
	 * 其余字符统一替换为 `.`。
	 */
	private val REPLACE_PATTERN: Pattern = Pattern.compile("[^\\w\\.:;, -]")

	fun escape(input: String?): String {
		if (input == null) {
			return "null"
		}
		return REPLACE_PATTERN.matcher(input).replaceAll(".")
	}

	fun escape(input: ByteArray?): String {
		if (input == null) {
			return "null"
		}
		return escape(String(input, StandardCharsets.UTF_8))
	}
}
