package kadx.tests.api.utils.assertj

import kadx.tests.api.utils.TestUtils
import org.assertj.core.api.AbstractStringAssert
import org.assertj.core.error.ShouldNotContainSubsequence.shouldNotContainSubsequence
import org.assertj.core.internal.Failures
import java.util.regex.Pattern
import java.util.stream.Collectors

/**
 * 反编译代码字符串的 AssertJ 断言封装（`assertThat(code).containsOne(...)` 等）。
 *
 * 所有方法名与 Java 版本保持一致，供已有的 Kotlin / Java 测试链式调用。
 */
class KadxCodeAssertions(code: String?) : AbstractStringAssert<KadxCodeAssertions>(code, KadxCodeAssertions::class.java) {

	private val failures: Failures = Failures.instance()

	fun containsOne(substring: String): KadxCodeAssertions = countString(1, substring)

	fun countString(count: Int, substring: String): KadxCodeAssertions {
		isNotNull()
		val actualCount = TestUtils.count(actual, substring)
		if (actualCount != count) {
			failWithMessage("Expected a substring <%s> count <%d> but was <%d>", substring, count, actualCount)
		}
		return this
	}

	fun notContainsLine(indent: Int, line: String): KadxCodeAssertions = countLine(0, indent, line)

	fun containsLine(indent: Int, line: String): KadxCodeAssertions = countLine(1, indent, line)

	private fun countLine(count: Int, indent: Int, line: String): KadxCodeAssertions {
		val indentStr = TestUtils.indent(indent)
		return countString(count, indentStr + line)
	}

	fun containsLines(vararg lines: String): KadxCodeAssertions = containsLines(0, *lines)

	fun containsLines(commonIndent: Int, vararg lines: String): KadxCodeAssertions {
		if (lines.size == 1) {
			return containsLine(commonIndent, lines[0])
		}
		val indent = TestUtils.indent(commonIndent)
		val sb = StringBuilder()
		for (line in lines) {
			sb.append('\n')
			if (line.isEmpty()) {
				// 空行不添加公共缩进
				continue
			}
			val searchLine = indent + line
			sb.append(searchLine)
			// 逐行断言，便于定位失败位置
			contains(searchLine)
		}
		return containsOnlyOnce(sb.substring(1))
	}

	fun doesNotContainSubsequence(vararg values: CharSequence): KadxCodeAssertions {
		val regex = values.joinToString(".*") { Pattern.quote(it.toString()) }
		val pattern = Pattern.compile(regex, Pattern.DOTALL)
		val matcher = pattern.matcher(actual)
		if (matcher.find()) {
			throw failures.failure(info, shouldNotContainSubsequence(actual, values, matcher.start()))
		}
		return this
	}

	fun removeBlockComments(): KadxCodeAssertions {
		val code = actual.replace("/\\*.*\\*/".toRegex(), "")
		val newCode = KadxCodeAssertions(code)
		newCode.print()
		return newCode
	}

	fun removeLineComments(): KadxCodeAssertions {
		val code = actual.replace("//.*(?!\$)".toRegex(), "")
		val newCode = KadxCodeAssertions(code)
		newCode.print()
		return newCode
	}

	fun print(): KadxCodeAssertions {
		println("-----------------------------------------------------------")
		println(actual)
		println("-----------------------------------------------------------")
		return this
	}

	fun containsOneOf(vararg substringArr: String): KadxCodeAssertions {
		var matches = 0
		for (substring in substringArr) {
			matches += TestUtils.count(actual, substring)
		}
		if (matches != 1) {
			failWithMessage("Expected only one match from <%s> but was <%d>", substringArr.contentToString(), matches)
		}
		return this
	}

	@SafeVarargs
	fun oneOf(vararg checks: (KadxCodeAssertions) -> KadxCodeAssertions): KadxCodeAssertions {
		var passed = 0
		val failed = ArrayList<Throwable>()
		for (check in checks) {
			try {
				check(this)
				passed++
			} catch (e: Throwable) {
				failed.add(e)
			}
		}
		if (passed != 1) {
			failWithMessage(
				"Expected only one match but passed: <%d>, failed: <%d>, details:\n<%s>",
				passed,
				failed.size,
				failed.stream().map { it.message }.collect(Collectors.joining("\nFailed check:\n ")),
			)
		}
		return this
	}
}
