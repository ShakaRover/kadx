package jadx.core.dex.nodes

import jadx.core.dex.attributes.AttrNode

/**
 * Lightweight replacement for BlockNode in regions.
 * Use with caution! Some passes still expect BlockNode in method blocks list (mth.getBlockNodes())
 */
class InsnContainer(insns: List<InsnNode>) :
	AttrNode(),
	IBlock {
	val insns: List<InsnNode> = insns

	constructor(insn: InsnNode) : this(arrayListOf(insn))

	override fun getInstructions(): List<InsnNode> = insns

	override fun baseString(): String = "IC"

	override fun toString(): String = "InsnContainer"
}
