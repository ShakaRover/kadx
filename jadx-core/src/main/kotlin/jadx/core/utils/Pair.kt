package jadx.core.utils

/**
 * 通用二元组（值语义）。
 *
 * **用途**：把两个同类型对象配对保存，例如 finally 遍历器里保存“匹配的指令对”。
 *
 * **为什么不使用 data class**：原 Java 版本的 `equals/hashCode` 是手写的，
 * data class 自动生成的 `hashCode` 公式与之不同，会改变以 Pair 作键的
 * HashMap/HashSet 行为，所以这里原样保留原有实现。
 */
class Pair<T>(
	val first: T,
	val second: T,
) {

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o !is Pair<*>) {
			return false
		}
		return first == o.first && second == o.second
	}

	override fun hashCode(): Int = first.hashCode() + 31 * second.hashCode()

	override fun toString(): String = "($first, $second)"
}
