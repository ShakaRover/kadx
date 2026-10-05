package kadx.core.utils.blocks

import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.EmptyBitSet
import java.util.ArrayList
import java.util.BitSet
import java.util.Collections
import java.util.NoSuchElementException
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer

/**
 * 基于 [BitSet] 的 [BlockNode] 集合实现。
 *
 * **设计意图**：方法内的基本块数量在分析期固定，用块在方法块列表中的下标作为位图索引，
 * 比 `HashSet<BlockNode>` 更省内存、遍历更快（尤其支配树/后支配树计算中会被大量创建）。
 *
 * **Kotlin 转换说明**：
 * - 静态工厂 [empty]/[from] 放入 `companion object` 并标注 `@JvmStatic`，
 *   Java 侧 `BlockSet.empty(mth)`/`BlockSet.from(...)` 不变；
 * - [contains] 加 `operator`，支持下游 Kotlin 的 `in`/`!in` 语法；
 * - 原 Java 的 `Iterable.forEach`/`spliterator` 覆写保留，保证 JVM 行为一致；
 * - `mth.getBasicBlocks()` 在 Kotlin 侧声明为可空（块处理前为 null），这里按原逻辑用
 *   [checkNotNull] 取非空（调用发生在块构建完成之后）。
 */
class BlockSet(private val mth: MethodNode) : Iterable<BlockNode> {

	/** 位图：第 i 位为 1 表示下标为 i 的基本块属于本集合 */
	private val bs: BitSet = BitSet(mthBlocks().size)

	companion object {
		/** 创建空集合。 */
		fun empty(mth: MethodNode): BlockSet = BlockSet(mth)

		/** 从给定块集合创建（复制内容，不影响原集合）。 */
		fun from(mth: MethodNode, blocks: Collection<BlockNode>): BlockSet {
			val newBS = BlockSet(mth)
			newBS.addAll(blocks)
			return newBS
		}
	}

	private fun mthBlocks(): List<BlockNode> = checkNotNull(mth.basicBlocks)

	operator fun contains(block: BlockNode): Boolean = bs.get(block.pos)

	fun add(block: BlockNode) {
		bs.set(block.pos)
	}

	fun addAll(blocks: Collection<BlockNode>) {
		blocks.forEach { add(it) }
	}

	/** 位图直接按位或，比逐个 add 更快。 */
	fun addAll(otherBlockSet: BlockSet) {
		bs.or(otherBlockSet.bs)
	}

	fun remove(block: BlockNode) {
		bs.clear(block.pos)
	}

	fun remove(blocks: Collection<BlockNode>) {
		blocks.forEach { remove(it) }
	}

	/** 加入并返回加入前的状态（用于“首次访问”判定）。 */
	fun addChecked(block: BlockNode): Boolean {
		val id = block.pos
		val state = bs.get(id)
		bs.set(id)
		return state
	}

	fun containsAll(blocks: List<BlockNode>): Boolean {
		for (block in blocks) {
			if (!contains(block)) {
				return false
			}
		}
		return true
	}

	fun intersects(blocks: List<BlockNode>): Boolean {
		for (block in blocks) {
			if (contains(block)) {
				return true
			}
		}
		return false
	}

	/** 返回“本集合 ∩ 给定块集合”的新集合。 */
	fun intersect(blocks: List<BlockNode>): BlockSet {
		val input = from(mth, blocks)
		val result = BlockSet(mth)
		val resultBS = result.bs
		resultBS.or(this.bs)
		resultBS.and(input.bs)
		return result
	}

	fun isEmpty(): Boolean = bs.isEmpty()

	fun size(): Int = bs.cardinality()

	fun remove() {
		bs.clear()
	}

	/** 恰好只有一个元素时返回该元素，否则返回 null。 */
	val one: BlockNode? get() {
		if (bs.cardinality() == 1) {
			return mthBlocks()[bs.nextSetBit(0)]
		}
		return null
	}

	val first: BlockNode get() = mthBlocks()[bs.nextSetBit(0)]

	override fun forEach(action: Consumer<in BlockNode>) {
		if (bs.isEmpty()) {
			return
		}
		val blocks = mthBlocks()
		var i = bs.nextSetBit(0)
		while (i >= 0) {
			action.accept(blocks[i])
			i = bs.nextSetBit(i + 1)
		}
	}

	override fun iterator(): Iterator<BlockNode> = BlockSetIterator(bs, size(), mthBlocks())

	override fun spliterator(): Spliterator<BlockNode> {
		val size = size()
		val iterator = BlockSetIterator(bs, size, mthBlocks())
		return Spliterators.spliterator(iterator, size.toLong(), Spliterator.ORDERED or Spliterator.DISTINCT)
	}

	/** 按块下标升序展开为列表。 */
	fun toList(): List<BlockNode> {
		if (bs === EmptyBitSet.EMPTY) {
			return Collections.emptyList()
		}
		val size = bs.cardinality()
		if (size == 0) {
			return Collections.emptyList()
		}
		val mthBlocks = mthBlocks()
		val blocks: MutableList<BlockNode> = ArrayList(size)
		var i = bs.nextSetBit(0)
		while (i >= 0) {
			blocks.add(mthBlocks[i])
			i = bs.nextSetBit(i + 1)
		}
		return blocks
	}

	override fun toString(): String = toList().toString()

	/** 位图迭代器：按位图顺序（即块下标升序）遍历，不做防御性拷贝。 */
	private class BlockSetIterator(
		private val bs: BitSet,
		private val size: Int,
		private val blocks: List<BlockNode>,
	) : Iterator<BlockNode> {

		private var cursor = 0
		private var start = 0

		override fun hasNext(): Boolean = cursor != size

		override fun next(): BlockNode {
			val pos = bs.nextSetBit(start)
			if (pos == -1) {
				throw NoSuchElementException()
			}
			start = pos + 1
			cursor++
			return blocks[pos]
		}
	}
}
