package jadx.core.dex.regions.loops

import jadx.api.ICodeWriter
import jadx.core.codegen.RegionGen
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.regions.conditions.ConditionRegion
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnUtils
import jadx.core.utils.exceptions.CodegenException

/**
 * `while` / `for` / `for-each` 等循环对应的区域。
 *
 * **结构**：
 * - [info]：CFG 层的循环信息（头块、回边、循环块集合）；
 * - [header]：条件头块；为 null 表示无限循环（[isEndless]）；
 * - [conditionAtEnd]：条件是否在循环体末尾判断（do-while 形式）；
 * - [preCondition]：每次判断条件前必须先执行的块（用于把 `a = ...; if (a)` 提升进条件）；
 * - [body]：循环体区域；
 * - [type]：循环类型（普通 for / for-each）。
 *
 * 条件本身由父类 [ConditionRegion] 维护。
 *
 * Kotlin 转换说明：
 * - 原 Java `header.instructions` 需要**原地修改**，所以这里用 BlockNode 的
 *   `instructions` 属性（可变 ArrayList），而不是只读的 `getInstructions()`；
 * - `getType()/getInfo()` 等保留显式方法名，Java 调用方零改动。
 */
class LoopRegion(
	parent: IRegion?,
	val info: LoopInfo,
	val header: BlockNode?,
	private val conditionAtEnd: Boolean,
) : ConditionRegion(parent) {

	/** 条件判断前需要先执行的块 */
	private var preCondition: BlockNode? = null

	private var bodyRegion: IRegion? = null

	private var typeValue: LoopType? = null

	init {
		if (header != null) {
			updateCondition(header)
		}
	}

	/** 没有条件头块 => 无限循环 `while (true)` */
	val isEndless: Boolean get() = header == null

	val body: IRegion? get() = bodyRegion

	fun setBody(body: IRegion?) {
		this.bodyRegion = body
	}

	val isConditionAtEnd: Boolean get() = conditionAtEnd

	/** 设置每次循环判断条件前必须先执行的块 */
	fun setPreCondition(preCondition: BlockNode) {
		this.preCondition = preCondition
	}

	/**
	 * 检查 [preCondition] 里的指令能否安全地内联进循环条件。
	 *
	 * 判定规则（保守）：
	 * 1. 每条指令都必须有结果寄存器；
	 * 2. 该结果只能被使用一次（不能有多个使用者）；
	 * 3. 结果必须在后续前置指令或条件表达式里被使用，否则内联会丢语义。
	 */
	fun checkPreCondition(): Boolean {
		val insns = checkNotNull(preCondition).instructions
		if (insns.isEmpty()) {
			return true
		}
		val condition = condition ?: return false
		val conditionArgs = condition.registerArgs
		if (conditionArgs.isEmpty()) {
			return false
		}
		val size = insns.size
		for (i in 0 until size) {
			val insn = insns[i]
			val res = insn.result ?: return false
			if (checkNotNull(res.sVar).useCount > 1) {
				return false
			}
			var found = false
			// 在其他前置指令中查找结果的使用
			for (j in i + 1 until size) {
				if (insns[i].containsVar(res)) {
					found = true
				}
			}
			// 或在条件表达式中查找
			if (!found && InsnUtils.containsVar(conditionArgs, res)) {
				found = true
			}
			if (!found) {
				return false
			}
		}
		return true
	}

	/**
	 * 把所有前置指令移动到条件头块之前（即合并进头块）。
	 *
	 * 合并后头块允许包含多条指令（[AFlag.ALLOW_MULTIPLE_INSNS_LOOP_COND]），
	 * 代码生成时会把它们一起内联进循环条件。
	 */
	fun mergePreCondition() {
		val preCondition = this.preCondition
		val header = this.header
		if (preCondition != null && header != null) {
			val condInsns = header.instructions
			val preCondInsns = preCondition.instructions
			preCondInsns.addAll(condInsns)
			condInsns.clear()
			condInsns.addAll(preCondInsns)
			header.add(AFlag.ALLOW_MULTIPLE_INSNS_LOOP_COND)
			preCondInsns.clear()
			this.preCondition = null
		}
	}

	val sourceLine: Int get() {
		val lastInsn = BlockUtils.getLastInsn(header)
		val headerLine = lastInsn?.getSourceLine() ?: 0
		if (headerLine != 0) {
			return headerLine
		}
		return conditionSourceLine
	}

	val type: LoopType? get() = typeValue

	fun setType(type: LoopType) {
		this.typeValue = type
	}

	override val subBlocks: List<IContainer> get() {
		val all = ArrayList<IContainer>(2 + conditionBlocks.size)
		val preCondition = this.preCondition
		if (preCondition != null) {
			all.add(preCondition)
		}
		all.addAll(conditionBlocks)
		val body = this.bodyRegion
		if (body != null) {
			all.add(body)
		}
		return all
	}

	override fun replaceSubBlock(oldBlock: IContainer, newBlock: IContainer): Boolean = false

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeLoop(this, code)
	}

	override fun baseString(): String = body?.baseString() ?: "-"

	override fun toString(): String = "LOOP:" + info.id + ": " + baseString()
}
