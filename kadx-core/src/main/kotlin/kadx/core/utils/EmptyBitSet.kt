package kadx.core.utils

import java.util.BitSet

/**
 * 不可变的“空 BitSet”单例实现。
 *
 * **用途**：支配树/后支配树里大量使用“空集合”占位。直接用普通空 `BitSet`
 * 会不断创建对象；这里用单例并把所有写操作变成异常/空操作，既省内存又能在
 * 误写时尽早暴露问题。
 *
 * **Kotlin 转换说明**：
 * - 继承 Java 的 [BitSet]，用 `override fun` 精确匹配 JVM 方法签名；
 * - [EMPTY] 是共享的单例常量；
 * - [serialVersionUID] 用 `const val` 生成静态字段，满足 Java 序列化要求。
 */
class EmptyBitSet : BitSet(0) {

	override fun cardinality(): Int = 0

	override fun isEmpty(): Boolean = true

	override fun nextSetBit(fromIndex: Int): Int = -1

	override fun length(): Int = 0

	override fun size(): Int = 0

	override fun set(bitIndex: Int): Unit = throw UnsupportedOperationException()

	override fun set(bitIndex: Int, value: Boolean): Unit = throw UnsupportedOperationException()

	override fun set(fromIndex: Int, toIndex: Int): Unit = throw UnsupportedOperationException()

	override fun set(fromIndex: Int, toIndex: Int, value: Boolean): Unit = throw UnsupportedOperationException()

	override fun get(bitIndex: Int): Boolean = false

	override fun get(fromIndex: Int, toIndex: Int): BitSet = EMPTY

	override fun and(set: BitSet): Unit = throw UnsupportedOperationException()

	override fun or(set: BitSet): Unit = throw UnsupportedOperationException()

	override fun xor(set: BitSet): Unit = throw UnsupportedOperationException()

	override fun andNot(set: BitSet): Unit = throw UnsupportedOperationException()

	companion object {
		private const val serialVersionUID: Long = -1194884945157778639L

		val EMPTY: BitSet = EmptyBitSet()
	}
}
