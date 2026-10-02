package jadx.core.utils

import java.util.Arrays
import java.util.NoSuchElementException
import java.util.Objects
import java.util.RandomAccess
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.UnaryOperator

/**
 * 简单不可变列表实现。
 *
 * 警告：部分方法未实现——所有会修改内容的方法一律抛 [UnsupportedOperationException]。
 *
 * **用途**：`Utils.lockList` 返回它表示“外部不可修改”的列表，减少防御性拷贝。
 *
 * **Kotlin 转换说明**：原 Java 实现 `java.util.List`，这里实现等价的 Kotlin
 * [MutableList]（JVM 擦除后同样是 `java.util.List`），Java 调用方零改动。
 */
class ImmutableList<E> :
	MutableList<E>,
	RandomAccess {
	private val arr: Array<E>

	constructor(col: Collection<E>) : this(toArrayCopy(col))

	constructor(arr: Array<E>) {
		this.arr = arr
	}

	override val size: Int get() = arr.size

	override fun isEmpty(): Boolean = arr.isEmpty()

	override fun get(index: Int): E = arr[index]

	override fun indexOf(element: E): Int {
		for (i in arr.indices) {
			if (Objects.equals(arr[i], element)) {
				return i
			}
		}
		return -1
	}

	override fun lastIndexOf(element: E): Int {
		for (i in arr.indices.reversed()) {
			if (Objects.equals(arr[i], element)) {
				return i
			}
		}
		return -1
	}

	override fun contains(element: E): Boolean = indexOf(element) != -1

	override fun containsAll(elements: Collection<E>): Boolean {
		for (obj in elements) {
			if (!contains(obj)) {
				return false
			}
		}
		return true
	}

	override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
		private val len = arr.size
		private var index = 0

		override fun hasNext(): Boolean = index < len

		override fun next(): E {
			try {
				return arr[index++]
			} catch (e: IndexOutOfBoundsException) {
				throw NoSuchElementException(e.message)
			}
		}

		override fun remove(): Unit = throw UnsupportedOperationException()
	}

	override fun forEach(action: Consumer<in E>) {
		for (e in arr) {
			action.accept(e)
		}
	}

	override fun add(element: E): Boolean = throw UnsupportedOperationException()

	override fun remove(element: E): Boolean = throw UnsupportedOperationException()

	override fun addAll(elements: Collection<E>): Boolean = throw UnsupportedOperationException()

	override fun addAll(index: Int, elements: Collection<E>): Boolean = throw UnsupportedOperationException()

	override fun removeAll(elements: Collection<E>): Boolean = throw UnsupportedOperationException()

	override fun retainAll(elements: Collection<E>): Boolean = throw UnsupportedOperationException()

	override fun replaceAll(operator: UnaryOperator<E>): Unit = throw UnsupportedOperationException()

	override fun sort(c: Comparator<in E>?): Unit = throw UnsupportedOperationException()

	override fun removeIf(filter: Predicate<in E>): Boolean = throw UnsupportedOperationException()

	override fun clear(): Unit = throw UnsupportedOperationException()

	override fun set(index: Int, element: E): E = throw UnsupportedOperationException()

	override fun add(index: Int, element: E): Unit = throw UnsupportedOperationException()

	override fun removeAt(index: Int): E = throw UnsupportedOperationException()

	override fun listIterator(): MutableListIterator<E> = throw UnsupportedOperationException()

	override fun listIterator(index: Int): MutableListIterator<E> = throw UnsupportedOperationException()

	override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> = throw UnsupportedOperationException()

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o is ImmutableList<*>) {
			return Arrays.equals(arr, o.arr)
		}
		if (o is List<*>) {
			val size = size
			if (size != o.size) {
				return false
			}
			for (i in 0 until size) {
				if (!Objects.equals(arr[i], o[i])) {
					return false
				}
			}
			return true
		}
		return false
	}

	override fun hashCode(): Int = Arrays.hashCode(arr)

	override fun toString(): String = "ImmutableList{" + Arrays.toString(arr) + '}'

	companion object {
		/** 把集合内容复制成数组（避免依赖 reified 的 Kotlin `toArray` 扩展）。 */
		@Suppress("UNCHECKED_CAST")
		private fun <E> toArrayCopy(col: Collection<E>): Array<E> {
			val arr = arrayOfNulls<Any?>(col.size) as Array<E>
			var i = 0
			for (e in col) {
				arr[i++] = e
			}
			return arr
		}
	}
}
