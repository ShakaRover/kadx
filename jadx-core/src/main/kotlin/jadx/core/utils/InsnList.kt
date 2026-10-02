package jadx.core.utils

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode

/**
 * 指令列表包装器：提供按“引用相等”（而非 `equals`）查找/删除指令的辅助方法。
 *
 * **为什么按引用**：同一条指令内容可能重复出现（如两个相同的 `const`），
 * 按内容删除会误删；指令是图中的节点，语义上应以对象身份为准。
 *
 * **Kotlin 转换说明**：静态方法用 `companion object` + `@JvmStatic`；
 * [remove] 会调用迭代器的 `remove()`，故参数声明为 [MutableList]
 * （JVM 擦除后仍是 `java.util.List`，Java 调用方零改动）。
 */
class InsnList(private val list: MutableList<InsnNode>) : Iterable<InsnNode> {

	companion object {
		@JvmStatic
		fun remove(list: MutableList<InsnNode>, insn: InsnNode) {
			val iterator = list.iterator()
			while (iterator.hasNext()) {
				val next = iterator.next()
				if (next === insn) {
					iterator.remove()
					return
				}
			}
		}

		@JvmStatic
		fun remove(block: BlockNode, insn: InsnNode) {
			remove(block.instructions, insn)
		}

		@JvmStatic
		fun getIndex(list: List<InsnNode>, insn: InsnNode): Int = getIndex(list, insn, 0)

		@JvmStatic
		fun getIndex(list: List<InsnNode>, insn: InsnNode, startOffset: Int): Int {
			val size = list.size
			for (i in startOffset until size) {
				if (list[i] === insn) {
					return i
				}
			}
			return -1
		}

		@JvmStatic
		fun contains(list: List<InsnNode>, insn: InsnNode): Boolean = getIndex(list, insn, 0) != -1

		@JvmStatic
		fun contains(list: List<InsnNode>, insn: InsnNode, startOffset: Int): Boolean = getIndex(list, insn, startOffset) != -1
	}

	fun getIndex(insn: InsnNode): Int = getIndex(list, insn)

	fun contains(insn: InsnNode): Boolean = getIndex(insn) != -1

	fun remove(insn: InsnNode) {
		remove(list, insn)
	}

	override fun iterator(): Iterator<InsnNode> = list.iterator()

	fun get(index: Int): InsnNode = list[index]

	fun size(): Int = list.size
}
