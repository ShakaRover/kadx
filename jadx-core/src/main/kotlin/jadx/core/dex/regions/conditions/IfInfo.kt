package jadx.core.dex.regions.conditions

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.blocks.BlockSet

/**
 * if 区域构建过程中的“条件信息”载体。
 *
 * **用途**：区域构建器（IfRegionMaker）在合并相邻 if 块时，需要携带：
 * - 方法 [mth]；
 * - 合并后的条件 [condition]；
 * - then / else 分支入口块 [thenBlock] / [elseBlock]；
 * - 参与条件的块集合 [mergedBlocks]（位图实现，省内存）；
 * - 需要跳过的块 [skipBlocks]；
 * - 需要强制内联的指令 [forceInlineInsns]；
 * - 合并后的出口块 [outBlock]。
 *
 * 这是构建期的临时数据对象，保持普通 class（身份语义）。
 *
 * Kotlin 转换说明：公开构造器与私有全参构造器分离；`getXxx()` 保持显式方法名，
 * 便于 Java 调用方零改动。静态工厂 [invert] 放入 companion + `@JvmStatic`。
 */
class IfInfo private constructor(
	private val mth: MethodNode,
	private val condition: IfCondition,
	private val thenBlock: BlockNode?,
	private val elseBlock: BlockNode?,
	private val mergedBlocks: BlockSet,
	private val skipBlocks: MutableSet<BlockNode>,
	private val forceInlineInsns: MutableList<InsnNode>,
) {

	constructor(mth: MethodNode, condition: IfCondition, thenBlock: BlockNode?, elseBlock: BlockNode?) :
		this(mth, condition, thenBlock, elseBlock, BlockSet.empty(mth), HashSet(), ArrayList())

	constructor(info: IfInfo, thenBlock: BlockNode?, elseBlock: BlockNode?) :
		this(
			info.getMth(),
			info.getCondition(),
			thenBlock,
			elseBlock,
			info.getMergedBlocks(),
			info.getSkipBlocks(),
			info.getForceInlineInsns(),
		)

	/** 合并后的出口块；构建过程中可能被设置 */
	private var outBlock: BlockNode? = null

	/** 把若干 IfInfo 的块集合、跳过块与内联指令合并进本对象 */
	fun merge(vararg arr: IfInfo) {
		for (info in arr) {
			mergedBlocks.addAll(info.getMergedBlocks())
			skipBlocks.addAll(info.getSkipBlocks())
			addInsnsForForcedInline(info.getForceInlineInsns())
		}
	}

	@Deprecated("Use getMergedBlocks().getFirst() instead")
	fun getFirstIfBlock(): BlockNode = mergedBlocks.getFirst()

	fun getMergedBlocks(): BlockSet = mergedBlocks

	fun getMth(): MethodNode = mth

	fun getCondition(): IfCondition = condition

	fun getSkipBlocks(): MutableSet<BlockNode> = skipBlocks

	fun getThenBlock(): BlockNode? = thenBlock

	fun getElseBlock(): BlockNode? = elseBlock

	fun getOutBlock(): BlockNode? = outBlock

	fun setOutBlock(outBlock: BlockNode?) {
		this.outBlock = outBlock
	}

	fun getForceInlineInsns(): MutableList<InsnNode> = forceInlineInsns

	fun resetForceInlineInsns() {
		forceInlineInsns.clear()
	}

	fun addInsnsForForcedInline(insns: List<InsnNode>) {
		forceInlineInsns.addAll(insns)
	}

	override fun toString(): String = "IfInfo: then: $thenBlock, else: $elseBlock"

	companion object {
		/** 生成条件取反、并交换 then/else 分支的新 IfInfo */
		@JvmStatic
		fun invert(info: IfInfo): IfInfo = IfInfo(
			info.getMth(),
			IfCondition.invert(info.getCondition()),
			info.getElseBlock(),
			info.getThenBlock(),
			info.getMergedBlocks(),
			info.getSkipBlocks(),
			info.getForceInlineInsns(),
		)
	}
}
