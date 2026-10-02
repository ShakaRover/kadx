package jadx.core.utils

import jadx.core.deobf.NameMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.HashSet
import java.util.Locale
import java.util.Objects

/**
 * 在两个候选名字之间选择“更好”的那个。
 *
 * **用途**：反混淆/重命名时，源码里的名字通常比混淆名更可读，
 * 该工具按“数字占比更低、长度更长”的启发式规则挑选。
 *
 * **Kotlin 转换说明**：`object` + `@JvmStatic`，Java 静态 import
 * （如测试里的 `import static jadx.core.utils.BetterName.getBetterClassName`）保持可用。
 */
object BetterName {

	private val LOG: Logger = LoggerFactory.getLogger(BetterName::class.java)

	private const val DEBUG = false

	private const val TOLERANCE = 0.001

	/**
	 * 比较两个类名并返回“更好”的一个；若同样好则返回 [firstName]。
	 */
	@JvmStatic
	fun getBetterClassName(firstName: String, secondName: String): String = getBetterName(firstName, secondName)

	/**
	 * 比较两个资源名并返回“更好”的一个；若同样好则返回 [firstName]。
	 */
	@JvmStatic
	fun getBetterResourceName(firstName: String, secondName: String): String = getBetterName(firstName, secondName)

	private fun getBetterName(firstName: String, secondName: String): String {
		if (Objects.equals(firstName, secondName)) {
			return firstName
		}

		if (StringUtils.isEmpty(firstName) || StringUtils.isEmpty(secondName)) {
			return if (StringUtils.notEmpty(firstName)) firstName else secondName
		}

		val firstResult = analyze(firstName)
		val secondResult = analyze(secondName)

		if (firstResult.digitCount != 0 || secondResult.digitCount != 0) {
			// 数字占比越低越好（混淆名里常带大量数字）
			val firstRatio = firstResult.digitCount.toFloat() / firstResult.length
			val secondRatio = secondResult.digitCount.toFloat() / secondResult.length

			if (Math.abs(secondRatio - firstRatio) >= TOLERANCE) {
				return if (firstRatio <= secondRatio) firstName else secondName
			}
		}

		// 长度更长通常信息量更大
		return if (firstResult.length >= secondResult.length) firstName else secondName
	}

	private fun analyze(name: String): AnalyzeResult {
		val result = AnalyzeResult()

		StringUtils.visitCodePoints(name) { cp ->
			if (Character.isDigit(cp)) {
				result.digitCount++
			}
			result.length++
		}

		return result
	}

	private class AnalyzeResult {
		var length: Int = 0
		var digitCount: Int = 0
	}

	@Deprecated("Use getBetterClassName or getBetterResourceName instead")
	@JvmStatic
	fun compareAndGet(first: String, second: String): String {
		if (Objects.equals(first, second)) {
			return first
		}
		val firstRating = calcRating(first)
		val secondRating = calcRating(second)
		val firstBetter = firstRating >= secondRating
		if (DEBUG) {
			if (firstBetter) {
				LOG.debug("Better name: '{}' > '{}' ({} > {})", first, second, firstRating, secondRating)
			} else {
				LOG.debug("Better name: '{}' > '{}' ({} > {})", second, first, secondRating, firstRating)
			}
		}
		return if (firstBetter) first else second
	}

	@Deprecated("Implementation detail of compareAndGet; should not be used outside tests")
	@JvmStatic
	fun calcRating(str: String): Int {
		var rating = str.length * 3
		rating += differentCharsCount(str) * 20

		if (NameMapper.isAllCharsPrintable(str)) {
			rating += 100
		}
		if (NameMapper.isValidIdentifier(str)) {
			rating += 50
		}
		if (str.contains("_")) {
			// 混淆名里很少出现下划线
			rating += 100
		}
		return rating
	}

	@Deprecated("Implementation detail of compareAndGet; should not be used outside tests")
	private fun differentCharsCount(str: String): Int {
		val lower = str.lowercase(Locale.ROOT)
		val chars: MutableSet<Int> = HashSet()
		StringUtils.visitCodePoints(lower) { chars.add(it) }
		return chars.size
	}
}
