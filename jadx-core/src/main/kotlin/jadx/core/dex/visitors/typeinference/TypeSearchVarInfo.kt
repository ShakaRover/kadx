package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.SSAVar

/**
 * 多变量类型搜索中，单个 SSA 变量的搜索状态。
 *
 * **算法意图**：[TypeSearch] 会为每个变量准备候选类型列表，
 * 然后像“多位计数器”一样逐个组合尝试：[nextType] 把当前变量切到下一个候选，
 * 当所有候选都试过并回到 0 号时返回 true，外层据此进位到下一个变量。
 * [constraints] 记录该变量参与的约束，用于 [TypeSearch.singleCheck] 校验。
 *
 * **Kotlin 转换说明**：
 * - 原字段名为 `var`（Kotlin 关键字），重命名为 [ssaVar]；
 * - 保留显式 `getXxx()/setXxx()` 方法名，[TypeSearch] 调用点零改动。
 */
class TypeSearchVarInfo(private val ssaVar: SSAVar) {
	private var typeResolved = false
	private var currentType: ArgType = ArgType.UNKNOWN
	private var candidateTypes: List<ArgType> = emptyList()
	private var currentIndex = -1
	private var constraints: List<ITypeConstraint> = emptyList()

	/** 标记变量类型已确定，清空候选列表。 */
	fun markResolved(type: ArgType) {
		this.currentType = type
		this.typeResolved = true
		this.candidateTypes = emptyList()
	}

	/** 重置到候选列表的第一个类型（已确定的变量不做处理）。 */
	fun reset() {
		if (typeResolved) {
			return
		}
		currentIndex = 0
		currentType = candidateTypes[0]
	}

	/**
	 * 切换到下一个候选类型。
	 *
	 * @return true 表示已回绕到第一个候选（即当前变量所有组合都已尝试）
	 */
	fun nextType(): Boolean {
		if (typeResolved) {
			return false
		}
		val len = candidateTypes.size
		currentIndex = (currentIndex + 1) % len
		currentType = candidateTypes[currentIndex]
		return currentIndex == 0
	}

	fun getVar(): SSAVar = ssaVar

	fun isTypeResolved(): Boolean = typeResolved

	fun setTypeResolved(typeResolved: Boolean) {
		this.typeResolved = typeResolved
	}

	fun getCurrentType(): ArgType = currentType

	fun setCurrentType(currentType: ArgType) {
		this.currentType = currentType
	}

	fun getCandidateTypes(): List<ArgType> = candidateTypes

	fun setCandidateTypes(candidateTypes: List<ArgType>) {
		this.candidateTypes = candidateTypes
	}

	fun getConstraints(): List<ITypeConstraint> = constraints

	fun setConstraints(constraints: List<ITypeConstraint>) {
		this.constraints = constraints
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(ssaVar.toShortString())
		if (typeResolved) {
			sb.append(", resolved type: ").append(currentType)
		} else {
			sb.append(", currentType=").append(currentType)
			sb.append(", candidateTypes=").append(candidateTypes)
			sb.append(", constraints=").append(constraints)
		}
		return sb.toString()
	}
}
