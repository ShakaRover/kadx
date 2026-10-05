package kadx.core.dex.nodes.utils

import kadx.core.dex.nodes.ClassNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * 从同名类列表中挑选“最佳”类。
 *
 * 场景：多个 dex 文件里可能出现完全同名的类。当前策略是优先选择来源文件名
 * 中 `classesN.dex` 的 N 最小的那个（`classes.dex` 视为 N=1）。
 *
 * Kotlin 转换说明：静态方法 [process] 用 companion + `@JvmStatic` 平替，
 * 使 Kotlin 侧仍可 `import ...SelectFromDuplicates.process`。
 */
class SelectFromDuplicates {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SelectFromDuplicates::class.java)

		/** 匹配 classes2.dex、classes10.dex 等（不含 classes.dex，后者单独处理）。 */
		private val CLASSES_DEX_PATTERN = Pattern.compile("classes([1-9]\\d*)\\.dex")

		/** 从候选列表中选出最佳类；列表为空时返回 null。 */
		fun process(dupClsList: List<ClassNode>): ClassNode? {
			var bestCls: ClassNode? = null
			var bestClsIndex = -1
			for (clsNode in dupClsList) {
				var selectCurrent = false
				if (bestCls == null) {
					selectCurrent = true
				} else {
					val clsIndex = getClassesIndex(clsNode.inputFileName)
					if (clsIndex != -1) {
						if (bestClsIndex != -1) {
							// 两者都有效时，索引更小的优先
							if (clsIndex < bestClsIndex) {
								selectCurrent = true
							}
						} else {
							// 有效的 dex 名优先
							selectCurrent = true
						}
					}
				}
				if (selectCurrent) {
					bestCls = clsNode
					bestClsIndex = getClassesIndex(clsNode.inputFileName)
				}
			}
			return bestCls
		}

		/**
		 * 从 `classesN.dex` 中解析 N。
		 *
		 * @return 非法来源名返回 -1；`classes.dex` 返回 1
		 */
		private fun getClassesIndex(source: String?): Int {
			if ("classes.dex" == source) {
				return 1
			}
			val src = source ?: return -1
			try {
				val matcher = CLASSES_DEX_PATTERN.matcher(src)
				if (!matcher.matches()) {
					return -1
				}
				val num = matcher.group(1)
				if (num == "1") {
					return -1
				}
				return Integer.parseInt(num)
			} catch (e: Exception) {
				LOG.debug("Failed to parse source classes index", e)
				return -1
			}
		}
	}
}
