package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.SSAVar

/**
 * 类型约束：在多变量搜索（[TypeSearch]）中用于校验一组相关变量类型是否自洽。
 *
 * **用途**：MOVE / PHI 等指令会把多个变量的类型绑定在一起，
 * 搜索算法需要在候选类型组合上调用 [check] 判断是否成立。
 */
interface ITypeConstraint {

	/** 该约束涉及的 SSA 变量集合 */
	val relatedVars: List<SSAVar>

	/** 在给定搜索状态下校验约束是否满足 */
	fun check(state: TypeSearchState): Boolean
}
