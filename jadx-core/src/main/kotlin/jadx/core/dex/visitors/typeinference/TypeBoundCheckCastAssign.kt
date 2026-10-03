package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.RootNode

/**
 * `check-cast` 的动态赋值边界：允许暂时忽略向下转型，直接采用被转换对象的类型。
 *
 * **算法意图**：`T x = (T) obj;` 里如果 `obj` 的实际类型比 `T` 更窄，
 * 说明这是一个多余的向下转型，后续会被删除。此边界返回更窄的真实类型，
 * 使类型推导能得到更精确的结果。
 *
 * **Kotlin 转换说明**：实现 [ITypeBoundDynamic]，同时保留
 * `getType()` 与 `getType(TypeUpdateInfo)` 两个重载，Java 调用方零改动。
 */
class TypeBoundCheckCastAssign(
	private val root: RootNode,
	val insn: IndexInsnNode,
) : ITypeBoundDynamic {

	override fun getBound(): BoundEnum = BoundEnum.ASSIGN

	override fun getType(updateInfo: TypeUpdateInfo): ArgType = getReturnType(updateInfo.getType(insn.getArg(0)))

	override fun getType(): ArgType = getReturnType(insn.getArg(0).getType())

	private fun getReturnType(argType: ArgType): ArgType {
		val castType = insn.indexAsType
		val result = root.typeCompare.compareTypes(argType, castType)
		// 若实际类型更窄，则忽略 cast 类型，直接使用实际类型
		return if (result.isNarrow()) argType else castType
	}

	override fun getArg(): RegisterArg? = insn.getResult()

	override fun toString(): String = "CHECK_CAST_ASSIGN{(" + insn.index + ") " + insn.getArg(0).getType() + "}"
}
