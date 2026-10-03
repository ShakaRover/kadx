package jadx.core.dex.instructions.mods

import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.regions.conditions.IfCondition
import jadx.core.utils.InsnUtils

/**
 * 三目运算指令（`cond ? a : b`），由 jadx 在区域构建阶段合成。
 *
 * 它额外持有条件表达式 [conditionRef]，条件里也可能引用指令，因此
 * [getRegisterArgs] / [visitInsns] / [replaceArg] / [rebindArgs] 都要连同条件一起处理。
 *
 * Kotlin 转换说明：条件字段在无参私有构造器里暂不赋值，故使用 `lateinit`；
 * 为避免属性自动生成的 `getCondition()` 与显式方法冲突，内部字段命名为 `conditionRef`，
 * 对外仍暴露原 Java 方法名 `getCondition()`。
 */
class TernaryInsn : InsnNode {

	private lateinit var conditionRef: IfCondition

	constructor(condition: IfCondition, result: RegisterArg?, th: InsnArg, els: InsnArg) : this() {
		setResult(result)

		if (th.isFalse() && els.isTrue()) {
			// 形如 `cond ? false : true`，取反后交换两分支
			this.conditionRef = IfCondition.invert(condition)
			addArg(els)
			addArg(th)
		} else {
			this.conditionRef = condition
			addArg(th)
			addArg(els)
		}
		visitInsns { this.inheritMetadata(it) }
	}

	private constructor() : super(InsnType.TERNARY, 2)

	val condition: IfCondition get() = conditionRef

	fun simplifyCondition() {
		conditionRef = IfCondition.simplify(conditionRef)
		if (conditionRef.mode == IfCondition.Mode.NOT) {
			invert()
		}
	}

	private fun invert() {
		conditionRef = IfCondition.invert(conditionRef)
		val tmp = getArg(0)
		setArg(0, getArg(1))
		setArg(1, tmp)
	}

	override fun getRegisterArgs(list: MutableCollection<RegisterArg>) {
		super.getRegisterArgs(list)
		list.addAll(conditionRef.registerArgs)
	}

	override fun replaceArg(from: InsnArg, to: InsnArg): Boolean {
		if (super.replaceArg(from, to)) {
			return true
		}
		return conditionRef.replaceArg(from, to)
	}

	override fun visitInsns(visitor: (InsnNode) -> Unit) {
		super.visitInsns(visitor)
		conditionRef.visitInsns(visitor)
	}

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is TernaryInsn || !super.isSame(obj)) {
			return false
		}
		return conditionRef == obj.conditionRef
	}

	override fun copy(): InsnNode {
		val copy = TernaryInsn()
		copy.conditionRef = conditionRef
		return copyCommonParams(copy)
	}

	override fun rebindArgs() {
		super.rebindArgs()
		for (reg in conditionRef.registerArgs) {
			val parentInsn = reg.getParentInsn()
			if (parentInsn != null) {
				parentInsn.rebindArgs()
			}
		}
	}

	override fun toString(): String = InsnUtils.formatOffset(offset) + ": TERNARY " +
		result + " = (" + conditionRef + ") ? " + getArg(0) + " : " + getArg(1)
}
