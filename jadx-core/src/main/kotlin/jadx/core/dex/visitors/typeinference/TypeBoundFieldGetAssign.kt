package jadx.core.dex.visitors.typeinference

import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.RootNode

/**
 * 实例字段读取（IGET）的动态赋值边界：用实例的泛型实参解析字段的泛型类型。
 *
 * **算法意图**：`List<String> list; ... list.get(0)` 这类字段读取，
 * 字段声明类型可能是 `T`（泛型变量），需要结合实例的真实类型
 * （这里是 `List<String>`）替换后得到精确的 `String`。
 *
 * **Kotlin 转换说明**：保留自定义 [equals]/[hashCode]（按 [getNode] 判等），
 * 边界会放进 `Set` 去重；`getType()` 与 `getType(TypeUpdateInfo)` 均保留。
 */
class TypeBoundFieldGetAssign(
	private val root: RootNode,
	private val getNode: IndexInsnNode,
	private val initType: ArgType,
) : ITypeBoundDynamic {

	private val fieldInfo: FieldInfo = getNode.index as FieldInfo

	override val bound: BoundEnum get() = BoundEnum.ASSIGN

	override fun getType(updateInfo: TypeUpdateInfo): ArgType = getResultType(updateInfo.getType(instanceArg))

	override val type: ArgType get() = getResultType(instanceArg.getType())

	private fun getResultType(instanceType: ArgType): ArgType {
		val resultGeneric = root.getTypeUtils().replaceClassGenerics(instanceType, initType)
		if (resultGeneric != null && !resultGeneric.isWildcard()) {
			return resultGeneric
		}
		// TODO: 检查该类型在当前作用域内是否允许
		return initType
	}

	private val instanceArg: InsnArg get() = getNode.getArg(0)

	override val arg: RegisterArg? get() = getNode.result

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o == null || javaClass != o.javaClass) {
			return false
		}
		val that = o as TypeBoundFieldGetAssign
		return getNode == that.getNode
	}

	override fun hashCode(): Int = getNode.hashCode()

	override fun toString(): String = "FieldGetAssign{" + fieldInfo + ", type=" + type + ", instanceArg=" + instanceArg + '}'
}
