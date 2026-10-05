package kadx.core.utils.blocks

import kadx.core.dex.nodes.BlockNode

/**
 * 一对基本块（[BlockNode]）的不可变值对象。
 *
 * **用途**：作为“边的两端/相邻块对”的轻量载体，常用于集合去重与图算法。
 *
 * **Kotlin 转换说明**：
 * - 这是图相关对象，**禁止使用 `data class`**：原 Java 手写了基于字段 `equals` 的判等，
 *   这里原样保留，避免自动生成 `componentN/copy` 改变既有语义；
 * - 字段用公开 `val` 暴露，编译器生成的 `getFirst()/getSecond()` 与原 Java 一致；
 * - [toString] 与原 Java 拼接格式完全一致。
 */
class BlockPair(
	val first: BlockNode,
	val second: BlockNode,
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is BlockPair) {
			return false
		}
		return first == other.first && second == other.second
	}

	override fun hashCode(): Int = first.hashCode() + 31 * second.hashCode()

	override fun toString(): String = "($first, $second)"
}
