package jadx.gui.search

import org.apache.commons.lang3.StringUtils
import java.util.regex.Pattern

/**
 * 搜索“匹配方法”接口。
 *
 * **做什么**：把“如何在给定文本里从 `start` 位置开始查找子串”抽象成一个策略。
 * 支持三种模式：
 * - 正则匹配（[SearchSettings.isUseRegex]）；
 * - 忽略大小写匹配；
 * - 普通大小写敏感匹配。
 *
 * **为什么声明为 `fun interface`**：原 Java 接口只有一个抽象方法 `find`，
 * 且 [build] 里用 lambda / 方法引用返回实现。Kotlin 只有 `fun interface`
 * 才允许 SAM 转换，同时它仍是一个普通 JVM 接口，Java 侧可以照常实现。
 *
 * **为什么 `build` 放在 companion + `@JvmStatic`**：原 Java 是 `static` 方法，
 * 这样 Java 调用方仍可写 `ISearchMethod.build(...)`。
 */
fun interface ISearchMethod {

	/**
	 * 在 [input] 中从 [start] 位置开始查找 [subStr]。
	 *
	 * @return 命中的起始下标；未命中返回 `-1`
	 */
	fun find(input: String, subStr: String, start: Int): Int

	companion object {
		/**
		 * 根据搜索设置构造匹配方法。
		 *
		 * 优先级：正则 > 忽略大小写 > 普通匹配（与原 Java 完全一致）。
		 */
		fun build(searchSettings: SearchSettings): ISearchMethod {
			if (searchSettings.isUseRegex) {
				val pattern: Pattern = searchSettings.pattern
				return ISearchMethod { input, _, start ->
					val matcher = pattern.matcher(input)
					if (matcher.find(start)) {
						matcher.start()
					} else {
						-1
					}
				}
			}
			if (searchSettings.isIgnoreCase) {
				return ISearchMethod(StringUtils::indexOfIgnoreCase)
			}
			return ISearchMethod(String::indexOf)
		}
	}
}
