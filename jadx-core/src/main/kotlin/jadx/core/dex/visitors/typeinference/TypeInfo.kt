package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.args.ArgType

/**
 * 单个 SSA 变量的类型信息：当前类型 + 所有约束边界。
 *
 * **算法意图**：类型推导为每个变量维护一个“当前类型”和一组边界
 * （[ITypeBound]），推导时在边界之间取最窄类型。
 *
 * **Kotlin 转换说明**：
 * - 字段改为私有属性，但保留显式 `getType()/setType()/getBounds()` 方法，
 *   使已有 Kotlin 调用点（`typeInfo.getType()` 等）与 Java 调用方都零改动；
 * - `bounds` 用 `LinkedHashSet` 保持插入顺序并去重。
 */
class TypeInfo {
	private var type: ArgType = ArgType.UNKNOWN

	val bounds: MutableSet<ITypeBound> = LinkedHashSet()

	fun getType(): ArgType = type

	fun setType(type: ArgType) {
		this.type = type
	}

	override fun toString(): String = "TypeInfo{type=$type, bounds=$bounds}"
}
