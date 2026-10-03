package jadx.gui.utils

import jadx.core.utils.Utils
import jadx.gui.treemodel.JNode

/**
 * 跳转历史中的一个位置（树节点 + 节点内偏移）。
 *
 * **做什么**：记录「跳到哪个 JNode 的哪个字符位置」，用于前进/后退导航。
 * 构造时会用 [JNode.getRootClass] 把节点归一化到根类，保证历史记录稳定。
 *
 * **为什么不是 `data class`**：该类有自定义的相等语义（[equals]/[hashCode]），
 * 且被当作身份对象在跳转列表中使用，必须保留手写实现。
 */
class JumpPosition {

	private val node: JNode
	private var pos: Int

	constructor(node: JNode) : this(node, node.getPos())

	constructor(node: JNode, pos: Int) {
		// getRootClass() 理论上非空，使用 getOrElse 与原 Java 行为保持一致
		this.node = Utils.getOrElse(node.getRootClass(), node)
		this.pos = pos
	}

	fun getPos(): Int = pos

	fun setPos(pos: Int) {
		this.pos = pos
	}

	fun getNode(): JNode = node

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is JumpPosition) {
			return false
		}
		// 原 Java 用 node.equals(...)，这里用 Kotlin 的 == 语义等价
		return pos == other.pos && node == other.node
	}

	override fun hashCode(): Int = 31 * node.hashCode() + pos

	override fun toString(): String = "Jump{$node:$pos}"
}
