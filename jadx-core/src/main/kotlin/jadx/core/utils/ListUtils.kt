package jadx.core.utils

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Enumeration
import java.util.LinkedHashSet
import java.util.Objects
import java.util.TreeSet
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.Predicate

/**
 * 列表/集合操作工具集。
 *
 * **用途**：提供一批“空安全”的集合操作（原 Java 允许传 null，需如实保留），
 * 以及按引用比较、去重、过滤等常用逻辑，供 jadx-core 各 Pass 使用。
 *
 * **Kotlin 转换说明**：全部为静态方法，用 `object` + `@JvmStatic` 保持 Java 调用不变。
 */
object ListUtils {

	@JvmStatic
	fun <T> mutableListOf(obj: T): List<T> {
		val list = ArrayList<T>()
		list.add(obj)
		return list
	}

	@JvmStatic
	fun <T> mutableListOf(obj1: T, obj2: T): List<T> {
		val list = ArrayList<T>()
		list.add(obj1)
		list.add(obj2)
		return list
	}

	@JvmStatic
	fun <T> mutableListOf(vararg objs: T): List<T> = ArrayList(Arrays.asList(*objs))

	@JvmStatic
	fun <T> isSingleElement(list: List<T>?, obj: T): Boolean {
		if (list == null || list.size != 1) {
			return false
		}
		return Objects.equals(list[0], obj)
	}

	@JvmStatic
	fun <T> unorderedEquals(first: List<T>, second: List<T>): Boolean {
		if (first.size != second.size) {
			return false
		}
		return first.containsAll(second)
	}

	@JvmStatic
	fun <T, U> orderedEquals(list1: List<T>, list2: List<U>, comparer: BiPredicate<T, U>): Boolean {
		if (list1 === list2) {
			return true
		}
		if (list1.size != list2.size) {
			return false
		}
		val iter1 = list1.iterator()
		val iter2 = list2.iterator()
		while (iter1.hasNext() && iter2.hasNext()) {
			val item1 = iter1.next()
			val item2 = iter2.next()
			if (!comparer.test(item1, item2)) {
				return false
			}
		}
		return !iter1.hasNext() && !iter2.hasNext()
	}

	@JvmStatic
	fun <T, R> map(list: Collection<T>?, mapFunc: Function<T, R>): List<R> {
		if (list == null || list.isEmpty()) {
			return Collections.emptyList()
		}
		val result = ArrayList<R>(list.size)
		for (t in list) {
			result.add(mapFunc.apply(t))
		}
		return result
	}

	@JvmStatic
	fun <T> first(list: List<T>): T = list[0]

	@JvmStatic
	fun <T> firstOrNull(list: List<T>?): T? {
		if (list == null || list.isEmpty()) {
			return null
		}
		return list[0]
	}

	@JvmStatic
	fun <T> last(list: List<T>?): T? {
		if (list == null || list.isEmpty()) {
			return null
		}
		return list[list.size - 1]
	}

	@JvmStatic
	fun <T> removeLast(list: List<T>?): T? {
		if (list == null) {
			return null
		}
		val size = list.size
		if (size == 0) {
			return null
		}
		return mutable(list).removeAt(size - 1)
	}

	/** 把只读视图转回可变列表：实际对象都是可变的，仅为通过编译期检查。 */
	@Suppress("UNCHECKED_CAST")
	private fun <T> mutable(list: List<T>): MutableList<T> = list as MutableList<T>

	@JvmStatic
	fun <T : Comparable<T>> distinctMergeSortedLists(first: List<T>, second: List<T>): List<T> {
		if (first.isEmpty()) {
			return second
		}
		if (second.isEmpty()) {
			return first
		}
		val set: MutableSet<T> = TreeSet(first)
		set.addAll(second)
		return ArrayList(set)
	}

	@JvmStatic
	fun <T> distinctList(list: List<T>): List<T> = ArrayList(LinkedHashSet(list))

	@JvmStatic
	@Suppress("UNCHECKED_CAST")
	fun <T> concat(first: T?, values: Array<T>): List<T> {
		val list = ArrayList<T>(1 + values.size)
		// 原 Java 允许 first 为 null（泛型不限制），这里用未检查转换保留同样语义
		list.add(first as T)
		list.addAll(Arrays.asList(*values))
		return list
	}

	/**
	 * 把旧元素替换为新元素。
	 * 兼容 null 与不可变空列表（`Collections.emptyList()` 产生的对象）。
	 */
	@JvmStatic
	fun <T> safeReplace(list: List<T>?, oldObj: T, newObj: T): List<T> {
		if (list == null || list.isEmpty()) {
			// 不可变空列表：必须新建
			val newList = ArrayList<T>(1)
			newList.add(newObj)
			return newList
		}
		val idx = list.indexOf(oldObj)
		if (idx != -1) {
			mutable(list)[idx] = newObj
		} else {
			mutable(list).add(newObj)
		}
		return list
	}

	@JvmStatic
	fun <T> safeRemove(list: List<T>?, obj: T) {
		if (list != null && !list.isEmpty()) {
			mutable(list).remove(obj)
		}
	}

	@JvmStatic
	fun <T> safeRemoveAndTrim(list: List<T>?, obj: T): List<T>? {
		if (list == null || list.isEmpty()) {
			return list
		}
		if (mutable(list).remove(obj)) {
			if (list.isEmpty()) {
				return Collections.emptyList()
			}
		}
		return list
	}

	@JvmStatic
	fun <T> safeAdd(list: List<T>?, obj: T): List<T> {
		if (list == null || list.isEmpty()) {
			val newList = ArrayList<T>(1)
			newList.add(obj)
			return newList
		}
		mutable(list).add(obj)
		return list
	}

	@JvmStatic
	fun <T> filter(list: Collection<T>?, filter: Predicate<T>): List<T> {
		if (list == null || list.isEmpty()) {
			return Collections.emptyList()
		}
		val result = ArrayList<T>()
		for (element in list) {
			if (filter.test(element)) {
				result.add(element)
			}
		}
		return result
	}

	/**
	 * 按条件查找“恰好一个”元素。
	 *
	 * @return 匹配 0 个或多个时返回 null
	 */
	@JvmStatic
	fun <T> filterOnlyOne(list: List<T>?, filter: Predicate<T>): T? {
		if (list == null || list.isEmpty()) {
			return null
		}
		var found: T? = null
		for (element in list) {
			if (filter.test(element)) {
				if (found != null) {
					// 找到第二个
					return null
				}
				found = element
			}
		}
		return found
	}

	@JvmStatic
	fun <T> allMatch(list: Collection<T>?, test: Predicate<T>): Boolean {
		if (list == null || list.isEmpty()) {
			return false
		}
		for (element in list) {
			if (!test.test(element)) {
				return false
			}
		}
		return true
	}

	@JvmStatic
	fun <T> noneMatch(list: Collection<T>?, test: Predicate<T>): Boolean = !anyMatch(list, test)

	@JvmStatic
	fun <T> anyMatch(list: Collection<T>?, test: Predicate<T>): Boolean {
		if (list == null || list.isEmpty()) {
			return false
		}
		for (element in list) {
			if (test.test(element)) {
				return true
			}
		}
		return false
	}

	@JvmStatic
	fun <T> enumerationToList(enumeration: Enumeration<T>?): List<T> {
		if (enumeration == null || enumeration === Collections.emptyEnumeration<T>()) {
			return Collections.emptyList()
		}
		val list = ArrayList<T>()
		while (enumeration.hasMoreElements()) {
			list.add(enumeration.nextElement())
		}
		return list
	}
}
