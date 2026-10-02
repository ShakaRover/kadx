package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg

/**
 * 类型边界：对某个 SSA 变量类型的一条限制。
 *
 * **用途**：类型推导为每个变量收集所有边界（[TypeInfo.getBounds]），
 * 再从中挑选最合适的类型。
 *
 * **Kotlin 转换说明**：接口方法保持与 Java 完全相同的 `getXxx()` 形式，
 * 以便 Java 子类（如 [TypeBoundInvokeUse]）继续用 `getBound()/getType()/getArg()` 覆写。
 */
interface ITypeBound {

	/** 该边界属于赋值方向还是使用方向 */
	fun getBound(): BoundEnum

	/** 边界给出的候选类型 */
	fun getType(): ArgType

	/** 关联的寄存器参数；常量边界可能没有关联参数，故可为空 */
	fun getArg(): RegisterArg?
}
