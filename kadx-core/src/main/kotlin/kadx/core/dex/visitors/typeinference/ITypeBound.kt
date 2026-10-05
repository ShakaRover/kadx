package kadx.core.dex.visitors.typeinference

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.RegisterArg

/**
 * 类型边界：对某个 SSA 变量类型的一条限制。
 *
 * **用途**：类型推导为每个变量收集所有边界（[TypeInfo.getBounds]），
 * 再从中挑选最合适的类型。
 *
 * **Kotlin 转换说明**：接口成员为 Kotlin 属性，JVM 上仍生成 `getBound()/getType()/getArg()`，
 * Java 子类（如 [TypeBoundInvokeUse]）的覆写方式保持不变。
 */
interface ITypeBound {

	/** 该边界属于赋值方向还是使用方向 */
	val bound: BoundEnum

	/** 边界给出的候选类型 */
	val type: ArgType

	/** 关联的寄存器参数；常量边界可能没有关联参数，故可为空 */
	val arg: RegisterArg?
}
