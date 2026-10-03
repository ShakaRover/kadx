package jadx.core.dex.regions.conditions

import jadx.api.ICodeWriter
import jadx.core.codegen.RegionGen
import jadx.core.dex.nodes.IBranchRegion
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.utils.exceptions.CodegenException
import java.util.Collections

/**
 * `if (cond) { ... } else { ... }` 对应的区域。
 *
 * **结构**：条件来自父类 [ConditionRegion]；[thenRegion] 与 [elseRegion] 分别是两个分支，
 * 二者都可能为 null（缺少分支）。[invert] 会在取反条件的同时交换两个分支。
 *
 * 同样属于区域树节点，保持普通 class（身份语义）。
 */
class IfRegion(parent: IRegion?) :
	ConditionRegion(parent),
	IBranchRegion {

	private var thenRegion: IContainer? = null
	private var elseRegion: IContainer? = null

	fun getThenRegion(): IContainer? = thenRegion

	fun setThenRegion(thenRegion: IContainer?) {
		this.thenRegion = thenRegion
	}

	fun getElseRegion(): IContainer? = elseRegion

	fun setElseRegion(elseRegion: IContainer?) {
		this.elseRegion = elseRegion
	}

	/** 条件取反并交换 then / else 分支 */
	fun invert() {
		invertCondition()
		// swap regions
		val tmp = thenRegion
		thenRegion = elseRegion
		elseRegion = tmp
	}

	val sourceLine: Int get() = getConditionSourceLine()

	override fun getSubBlocks(): List<IContainer> {
		val conditionBlocks = getConditionBlocks()
		val all = ArrayList<IContainer>(conditionBlocks.size + 2)
		all.addAll(conditionBlocks)
		val thenRegion = this.thenRegion
		if (thenRegion != null) {
			all.add(thenRegion)
		}
		val elseRegion = this.elseRegion
		if (elseRegion != null) {
			all.add(elseRegion)
		}
		return Collections.unmodifiableList(all)
	}

	/** 分支列表允许包含 null（表示缺失的分支） */
	override fun getBranches(): List<IContainer?> {
		val branches = ArrayList<IContainer?>(2)
		branches.add(thenRegion)
		branches.add(elseRegion)
		return Collections.unmodifiableList(branches)
	}

	override fun replaceSubBlock(oldBlock: IContainer, newBlock: IContainer): Boolean {
		if (oldBlock === thenRegion) {
			thenRegion = newBlock
			updateParent(newBlock, this)
			return true
		}
		if (oldBlock === elseRegion) {
			elseRegion = newBlock
			updateParent(newBlock, this)
			return true
		}
		return false
	}

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeIf(this, code, true)
	}

	override fun baseString(): String {
		val sb = StringBuilder()
		thenRegion?.let { sb.append(it.baseString()) }
		elseRegion?.let { sb.append(it.baseString()) }
		return sb.toString()
	}

	override fun toString(): String = "IF " + getConditionBlocks() + " THEN: " + thenRegion + " ELSE: " + elseRegion
}
