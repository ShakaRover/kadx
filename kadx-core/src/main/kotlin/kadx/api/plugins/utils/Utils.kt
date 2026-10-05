package kadx.api.plugins.utils

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Objects
import java.util.function.Function

/**
 * 插件通用集合/格式化工具。
 *
 * **做什么**：提供可空元素追加、列表拼接去重、列表转字符串、偏移量格式化、不可变集合构造等静态方法。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 是纯静态工具类；
 * `object` 表达单例，`@JvmStatic` 让 Java 侧静态调用与静态导入保持不变。
 * 参数使用 Kotlin 的 `MutableCollection` / `List`（字节码擦除后同为 `java.util.Collection` / `List`）。
 */
object Utils {

	/** 追加非空元素到列表。 */
	@JvmStatic
	fun <T> addToList(list: MutableCollection<T>, item: T?) {
		if (item != null) {
			list.add(item)
		}
	}

	/** 先用 [map] 转换元素，结果非空时再追加到列表。 */
	@JvmStatic
	fun <T, I> addToList(list: MutableCollection<T>, item: I?, map: Function<I, T>) {
		if (item != null) {
			val value = map.apply(item)
			if (value != null) {
				list.add(value)
			}
		}
	}

	/** 拼接两个列表；任一为空时直接返回另一个（可能返回原列表引用）。 */
	@JvmStatic
	fun <T> concat(a: List<T>, b: List<T>): List<T> {
		val aSize = a.size
		val bSize = b.size
		if (aSize == 0 && bSize == 0) {
			return Collections.emptyList()
		}
		if (aSize == 0) {
			return b
		}
		if (bSize == 0) {
			return a
		}
		val list = ArrayList<T>(aSize + bSize)
		list.addAll(a)
		list.addAll(b)
		return list
	}

	/** 拼接两个列表并按出现顺序去重。返回可变列表以兼容原 Java 返回 `java.util.List` 时调用方的可变用法。 */
	@JvmStatic
	fun <T> concatDistinct(a: List<T>, b: List<T>): MutableList<T> {
		val aSize = a.size
		val bSize = b.size
		if (aSize == 0 && bSize == 0) {
			return Collections.emptyList()
		}
		if (aSize == 0) {
			@Suppress("UNCHECKED_CAST")
			return b as MutableList<T>
		}
		if (bSize == 0) {
			@Suppress("UNCHECKED_CAST")
			return a as MutableList<T>
		}
		val set = LinkedHashSet<T>(aSize + bSize)
		set.addAll(a)
		set.addAll(b)
		return ArrayList(set)
	}

	/** 把列表转成 `a, b, c` 形式；null 返回 "null"，空列表返回空串。 */
	@JvmStatic
	fun <T> listToStr(list: List<T>?): String {
		if (list == null) {
			return "null"
		}
		if (list.isEmpty()) {
			return ""
		}
		if (list.size == 1) {
			return Objects.toString(list[0])
		}
		val sb = StringBuilder()
		val it = list.iterator()
		sb.append(it.next())
		while (it.hasNext()) {
			sb.append(", ").append(it.next())
		}
		return sb.toString()
	}

	/** 把整数偏移格式化为 `0x%04x`。 */
	@JvmStatic
	fun formatOffset(offset: Int): String = String.format("0x%04x", offset)

	/** 由可变参数构造一个不可变集合（去重）。 */
	@JvmStatic
	fun <T> constSet(vararg arr: T): Set<T> = setOf(*arr)
}
