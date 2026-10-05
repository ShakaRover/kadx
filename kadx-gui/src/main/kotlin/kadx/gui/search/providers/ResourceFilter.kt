package kadx.gui.search.providers

import kadx.api.resources.ResourceContentType
import kadx.api.resources.ResourceContentType.CONTENT_BINARY
import kadx.api.resources.ResourceContentType.CONTENT_TEXT
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.InvalidDataException
import java.util.EnumSet

/**
 * 资源搜索过滤器。
 *
 * **做什么**：把用户在“资源扩展名”输入框里填写的过滤表达式（如 `$TEXT|xml`）
 * 解析成“内容类型集合 + 扩展名集合”，用于决定哪些资源参与搜索。
 *
 * **为什么保留静态方法 / `DEFAULT_STR`**：`SearchDialog` 与 `ProjectData` 会按
 * `ResourceFilter.parse(...)`、`ResourceFilter.DEFAULT_STR` 访问，故用
 * companion + `@JvmStatic` / `@JvmField` 保持原静态形态。
 *
 * **为什么不是 `data class`**：它是不可变的配置对象，含缓存语义且按身份使用。
 */
class ResourceFilter private constructor(
	contentTypes: Set<ResourceContentType>,
	extSet: Set<String>,
) {

	private val anyFile: Boolean = contentTypes.isEmpty() && extSet.isEmpty()
	private val contentTypes: Set<ResourceContentType> = if (contentTypes.isEmpty()) emptySet() else contentTypes
	private val extSet: Set<String> = if (extSet.isEmpty()) emptySet() else extSet

	val isAnyFile: Boolean get() = anyFile

	fun getContentTypes(): Set<ResourceContentType> = contentTypes

	fun getExtSet(): Set<String> = extSet

	override fun toString(): String = format(this)

	companion object {
		private val ANY = ResourceFilter(emptySet(), emptySet())

		private const val VAR_TEXT = "\$TEXT"
		private const val VAR_BIN = "\$BIN"

		val DEFAULT_STR: String = VAR_TEXT

		/**
		 * 解析过滤表达式。空串或 `*` 表示不过滤任何资源。
		 *
		 * 表达式按 `|`、`,`、空格切分；以 `$` 开头的是内容类型变量，其余视为扩展名。
		 */
		fun parse(filterStr: String): ResourceFilter {
			val str = filterStr.trim()
			if (str.isEmpty() || str == "*") {
				return ANY
			}
			val contentTypes: MutableSet<ResourceContentType> = EnumSet.noneOf(ResourceContentType::class.java)
			val extSet: MutableSet<String> = LinkedHashSet()
			// 注意：这里必须用正则切分（原 Java 的 split 参数是正则），
			// Kotlin 的 String.split(String) 是字面量切分，语义不同。
			val parts = filterStr.split("[|, ]".toRegex())
			for (part in parts) {
				if (part.isEmpty()) {
					continue
				}
				if (part.startsWith("$")) {
					when (part) {
						VAR_TEXT -> contentTypes.add(CONTENT_TEXT)
						VAR_BIN -> contentTypes.add(CONTENT_BINARY)
						else -> throw InvalidDataException("Unknown var name: $part")
					}
				} else {
					extSet.add(part)
				}
			}
			return ResourceFilter(contentTypes, extSet)
		}

		/** 把过滤器格式化回表达式字符串。 */
		fun format(filter: ResourceFilter): String {
			if (filter.isAnyFile) {
				return "*"
			}
			val list: MutableList<String> = ArrayList()
			val types = filter.getContentTypes()
			if (types.contains(CONTENT_TEXT)) {
				list.add(VAR_TEXT)
			}
			if (types.contains(CONTENT_BINARY)) {
				list.add(VAR_BIN)
			}
			list.addAll(filter.getExtSet())
			return Utils.listToString(list, "|")
		}

		/** 在保留扩展名过滤的前提下，替换内容类型集合。 */
		fun withContentType(filterStr: String, contentTypes: Set<ResourceContentType>): String {
			val filter = parse(filterStr)
			return format(ResourceFilter(contentTypes, filter.getExtSet()))
		}
	}
}
