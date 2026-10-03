package jadx.core.dex.regions

import jadx.api.ICodeWriter
import jadx.core.codegen.RegionGen
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.CodegenException

/**
 * 最简单的区域：一个有序的子容器列表（顺序执行）。
 *
 * 例如一段没有任何分支的直线代码，或循环体、synchronized 体等内部块，
 * 都用 [Region] 表示。子容器按添加顺序依次生成代码。
 *
 * 注意：这是 CFG/区域树节点，**不是值对象**，因此保持普通 class（身份语义），
 * 不使用 `data class`。
 */
class Region(parent: IRegion?) : AbstractRegion(parent) {

	/** 子容器列表；初始容量 1 覆盖大多数“只有一个子块”的场景 */
	private val blocks: MutableList<IContainer> = ArrayList(1)

	override val subBlocks: List<IContainer> get() = blocks

	/** 追加一个子容器，并把它的父指针指向本区域 */
	fun add(region: IContainer) {
		updateParent(region, this)
		blocks.add(region)
	}

	/** 顺序生成所有子容器的代码 */
	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		for (c in blocks) {
			regionGen.makeRegion(code, c)
		}
	}

	/**
	 * 用 [newBlock] 替换列表中的 [oldBlock]，并同步其父指针。
	 *
	 * 这里用 `indexOf` 做身份比较（IContainer 未覆写 equals），与原 Java 行为一致。
	 */
	override fun replaceSubBlock(oldBlock: IContainer, newBlock: IContainer): Boolean {
		val i = blocks.indexOf(oldBlock)
		if (i != -1) {
			blocks[i] = newBlock
			updateParent(newBlock, this)
			return true
		}
		return false
	}

	/** 用于调试输出的简短标识：`(子块数量:块1|块2…)` */
	override fun baseString(): String {
		val sb = StringBuilder()
		val size = blocks.size
		sb.append('(')
		sb.append(size)
		if (size > 0) {
			sb.append(':')
			Utils.listToString(sb, blocks, "|") { it.baseString() }
		}
		sb.append(')')
		return sb.toString()
	}

	override fun toString(): String = 'R' + baseString()
}
