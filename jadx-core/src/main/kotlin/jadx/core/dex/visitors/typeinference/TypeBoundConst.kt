package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg
import java.util.Objects

/**
 * 固定（常量）类型边界：直接给出一个确定的候选类型。
 *
 * **算法意图**：大多数边界都是“类型就是 X”，例如常量指令的赋值类型、
 * 字段初始化类型、方法参数声明类型等，用本类表达最简单。
 *
 * **Kotlin 转换说明**：
 * - 保留自定义 [equals]/[hashCode]（按 [bound] + [type] 判等），
 *   因为边界会被放入 `Set`（如 [TypeInfo.getBounds]）去重；
 * - 保留两个构造器重载（两参/三参），Java 调用方零改动。
 */
class TypeBoundConst(
	override val bound: BoundEnum,
	override val type: ArgType,
	override val arg: RegisterArg?,
) : ITypeBound {

	constructor(bound: BoundEnum, type: ArgType) : this(bound, type, null)

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o == null || javaClass != o.javaClass) {
			return false
		}
		val that = o as TypeBoundConst
		return bound == that.bound && type == that.type
	}

	override fun hashCode(): Int = Objects.hash(bound, type)

	override fun toString(): String = "{" + bound + ": " + type + '}'
}
