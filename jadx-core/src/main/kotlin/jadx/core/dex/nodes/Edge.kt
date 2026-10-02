package jadx.core.dex.nodes

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AttrNode

class Edge @JvmOverloads constructor(
	val source: BlockNode,
	val target: BlockNode,
	isSynthetic: Boolean = false,
) : AttrNode() {
	init {
		if (isSynthetic) {
			add(AFlag.SYNTHETIC)
		}
	}

	fun isSynthetic(): Boolean = contains(AFlag.SYNTHETIC)

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Edge) return false
		return source == other.source && target == other.target
	}

	override fun hashCode(): Int = source.hashCode() + 31 * target.hashCode()

	override fun toString(): String = "Edge: $source -> $target"
}
