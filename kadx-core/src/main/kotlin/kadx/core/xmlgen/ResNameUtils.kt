package kadx.core.xmlgen

import kadx.core.deobf.NameMapper

/**
 * 资源名称（resource name）合法性处理工具。
 *
 * 目标：让名称既能被 aapt2 当作资源条目名，又能转换成合法的 `R` 类字段名。
 * 原 Java 为包级私有工具类，这里用 `object` 单例，调用 `ResNameUtils.xxx(...)` 不变。
 */
object ResNameUtils {

	/**
	 * 把 [name] 清洗为合法资源名；若发生改动则追加 [postfix]。
	 *
	 * [allowNonPrintable] 为 true 时允许非 ASCII 可打印字符（仍要求是合法标识符）。
	 */
	fun sanitizeAsResourceName(name: String, postfix: String, allowNonPrintable: Boolean): String {
		if (name.isEmpty()) {
			return postfix
		}

		val sb = StringBuilder(name.length + 1)
		var nameChanged = false

		var cp = name.codePointAt(0)
		if (isValidResourceNameStart(cp, allowNonPrintable)) {
			sb.appendCodePoint(cp)
		} else {
			sb.append('_')
			nameChanged = true

			if (isValidResourceNamePart(cp, allowNonPrintable)) {
				sb.appendCodePoint(cp)
			}
		}

		var i = Character.charCount(cp)
		while (i < name.length) {
			cp = name.codePointAt(i)
			if (isValidResourceNamePart(cp, allowNonPrintable)) {
				sb.appendCodePoint(cp)
			} else {
				sb.append('_')
				nameChanged = true
			}
			i += Character.charCount(cp)
		}

		val sanitizedName = sb.toString()
		if (NameMapper.isReserved(sanitizedName)) {
			nameChanged = true
		}

		return if (nameChanged) sanitizedName + postfix else sanitizedName
	}

	/** 把资源名转换为 `R` 类字段名（点号替换为下划线）。 */
	fun convertToRFieldName(resourceName: String): String = resourceName.replace('.', '_')

	/** 码点能否作为资源名首字符（aapt2 + R 类生成双重约束）。 */
	private fun isValidResourceNameStart(codePoint: Int, allowNonPrintable: Boolean): Boolean = (allowNonPrintable || NameMapper.isPrintableAsciiCodePoint(codePoint)) &&
		(isValidAapt2ResourceNameStart(codePoint) && NameMapper.isValidIdentifierStart(codePoint))

	/** 码点能否作为资源名非首字符（aapt2 + R 类生成双重约束，额外允许 `.`）。 */
	private fun isValidResourceNamePart(codePoint: Int, allowNonPrintable: Boolean): Boolean = (allowNonPrintable || NameMapper.isPrintableAsciiCodePoint(codePoint)) &&
		((isValidAapt2ResourceNamePart(codePoint) && NameMapper.isValidIdentifierPart(codePoint)) || codePoint == '.'.code)

	/** aapt2 规则：首字符为 XID_Start 或下划线。 */
	private fun isValidAapt2ResourceNameStart(codePoint: Int): Boolean = isXidStart(codePoint) || codePoint == '_'.code

	/** aapt2 规则：非首字符为 XID_Continue、`.` 或 `-`。 */
	private fun isValidAapt2ResourceNamePart(codePoint: Int): Boolean = isXidContinue(codePoint) || codePoint == '.'.code || codePoint == '-'.code

	private fun isXidStart(codePoint: Int): Boolean {
		// TODO: Need to implement a full check if the code point is XID_Start.
		return codePoint < 0x0370 && Character.isUnicodeIdentifierStart(codePoint)
	}

	private fun isXidContinue(codePoint: Int): Boolean {
		// TODO: Need to implement a full check if the code point is XID_Continue.
		return codePoint < 0x0370 &&
			(Character.isUnicodeIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint))
	}
}
