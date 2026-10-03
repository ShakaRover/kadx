package jadx.core.deobf

import jadx.core.utils.StringUtils
import java.util.regex.Pattern

/**
 * Java 标识符（identifier）合法性校验与非法字符清理工具。
 *
 * **用途**：反混淆 / 重命名时需要保证生成的类名、字段名、方法名是合法的 Java 标识符，
 * 否则反编译出的源码无法编译。本类提供：
 * - 合法性判断（[isValidIdentifier] / [isValidFullIdentifier]）；
 * - 保留字判断（[isReserved]）；
 * - 可打印字符判断（避免生成不可见字符）；
 * - 非法字符清理（[removeInvalidChars] / [removeInvalidCharsMiddle] / [removeNonPrintableCharacters]）。
 *
 * **Kotlin 转换说明**：全部是静态工具方法，放入 `companion object` 并用 `@JvmStatic`
 * 暴露；[VALID_JAVA_IDENTIFIER] 是被 Java 以字段方式访问的常量，用 `@JvmField` 保持
 * 静态字段的 JVM 表面不变。私有构造器保持“不可实例化”。
 */
class NameMapper private constructor() {

	companion object {
		/** 合法的单个 Java 标识符正则（可包含 Unicode 标识符字符） */
		@JvmField
		val VALID_JAVA_IDENTIFIER: Pattern = Pattern.compile(
			"\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*",
		)

		/** 合法的“全限定名”正则（用 `.` 连接多个标识符） */
		private val VALID_JAVA_FULL_IDENTIFIER: Pattern = Pattern.compile(
			"(" + VALID_JAVA_IDENTIFIER + "\\.)*" + VALID_JAVA_IDENTIFIER,
		)

		/** Java 保留字与字面量关键字（`_` 在 Java 9+ 也是保留字） */
		private val RESERVED_NAMES: Set<String> = setOf(
			"_",
			"abstract",
			"assert",
			"boolean",
			"break",
			"byte",
			"case",
			"catch",
			"char",
			"class",
			"const",
			"continue",
			"default",
			"do",
			"double",
			"else",
			"enum",
			"extends",
			"false",
			"final",
			"finally",
			"float",
			"for",
			"goto",
			"if",
			"implements",
			"import",
			"instanceof",
			"int",
			"interface",
			"long",
			"native",
			"new",
			"null",
			"package",
			"private",
			"protected",
			"public",
			"return",
			"short",
			"static",
			"strictfp",
			"super",
			"switch",
			"synchronized",
			"this",
			"throw",
			"throws",
			"transient",
			"true",
			"try",
			"void",
			"volatile",
			"while",
		)

		fun isReserved(str: String): Boolean = RESERVED_NAMES.contains(str)

		fun isValidIdentifier(str: String?): Boolean {
			if (!StringUtils.notEmpty(str)) {
				return false
			}
			val name = checkNotNull(str)
			return !isReserved(name) && VALID_JAVA_IDENTIFIER.matcher(name).matches()
		}

		fun isValidFullIdentifier(str: String?): Boolean {
			if (!StringUtils.notEmpty(str)) {
				return false
			}
			val name = checkNotNull(str)
			return !isReserved(name) && VALID_JAVA_FULL_IDENTIFIER.matcher(name).matches()
		}

		fun isValidAndPrintable(str: String?): Boolean {
			if (!isValidIdentifier(str)) {
				return false
			}
			return isAllCharsPrintable(checkNotNull(str))
		}

		fun isValidIdentifierStart(codePoint: Int): Boolean = Character.isJavaIdentifierStart(codePoint)

		fun isValidIdentifierPart(codePoint: Int): Boolean = Character.isJavaIdentifierPart(codePoint)

		fun isPrintableChar(c: Char): Boolean = 32 <= c.code && c.code <= 126

		fun isPrintableAsciiCodePoint(c: Int): Boolean = 32 <= c && c <= 126

		fun isPrintableCodePoint(codePoint: Int): Boolean {
			if (Character.isISOControl(codePoint)) {
				return false
			}
			if (Character.isWhitespace(codePoint)) {
				// 除标准空格外，其它空白字符一律不打印
				return codePoint == ' '.code
			}
			when (Character.getType(codePoint)) {
				Character.CONTROL.toInt(),
				Character.FORMAT.toInt(),
				Character.PRIVATE_USE.toInt(),
				Character.SURROGATE.toInt(),
				Character.UNASSIGNED.toInt(),
				-> return false
			}
			return true
		}

		fun isAllCharsPrintable(str: String): Boolean {
			val len = str.length
			var offset = 0
			while (offset < len) {
				val codePoint = str.codePointAt(offset)
				if (!isPrintableAsciiCodePoint(codePoint)) {
					return false
				}
				offset += Character.charCount(codePoint)
			}
			return true
		}

		/**
		 * 清理字符串中**非首字符**位置的非法字符，返回清理后的名字。
		 *
		 * 删除两类字符：
		 * - 不可打印字符（含非 ASCII 的 Unicode 字符）；
		 * - 不能作为 Java 标识符后续字符的字符。
		 *
		 * 注意：本方法用于“已带前缀”的名字，因此**不检查首字符是否合法**（允许数字开头），
		 * 也不检查保留字。
		 */
		fun removeInvalidCharsMiddle(name: String): String {
			if (isValidIdentifier(name) && isAllCharsPrintable(name)) {
				return name
			}
			val len = name.length
			val sb = StringBuilder(len)
			StringUtils.visitCodePoints(name) { codePoint ->
				if (isPrintableAsciiCodePoint(codePoint) && isValidIdentifierPart(codePoint)) {
					sb.appendCodePoint(codePoint)
				}
			}
			return sb.toString()
		}

		/**
		 * 清理非法字符；若首字符不是合法的标识符起始字符，则在前面补上 [prefix]。
		 *
		 * 参见 [removeInvalidCharsMiddle]。
		 */
		fun removeInvalidChars(name: String, prefix: String): String {
			val result = removeInvalidCharsMiddle(name)
			if (result.isNotEmpty()) {
				val codePoint = result.codePointAt(0)
				if (!isValidIdentifierStart(codePoint)) {
					return prefix + result
				}
			}
			return result
		}

		/** 仅删除不可打印字符，保留其余字符（用于日志/展示场景）。 */
		fun removeNonPrintableCharacters(name: String): String {
			val sb = StringBuilder(name.length)
			StringUtils.visitCodePoints(name) { codePoint ->
				if (isPrintableAsciiCodePoint(codePoint)) {
					sb.appendCodePoint(codePoint)
				}
			}
			return sb.toString()
		}
	}
}
