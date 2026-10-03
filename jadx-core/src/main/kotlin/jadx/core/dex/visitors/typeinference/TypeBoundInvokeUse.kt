package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.RootNode

/**
 * 泛型方法调用的“使用”动态边界：参数类型由实例的泛型实参计算得出。
 *
 * **算法意图**：`List<String> list; ... list.add(x)` 中参数声明类型可能是 `T`，
 * 需要结合实例真实类型（这里是 `List<String>`）替换后得到 `String`。
 * 若无法替换，则退回参数自身的当前类型。
 *
 * **Kotlin 转换说明**：保留自定义 [equals]/[hashCode]（按 [invokeNode] 判等），
 * 因为边界会被放入 `Set` 去重；`getType()` 与 `getType(TypeUpdateInfo)` 均保留。
 */
class TypeBoundInvokeUse(
	private val root: RootNode,
	private val invokeNode: BaseInvokeNode,
	override val arg: RegisterArg,
	private val genericArgType: ArgType,
) : ITypeBoundDynamic {

	override val bound: BoundEnum get() = BoundEnum.USE

	override fun getType(updateInfo: TypeUpdateInfo): ArgType = getArgType(updateInfo.getType(checkNotNull(invokeNode.getInstanceArg())), updateInfo.getType(arg))

	override val type: ArgType get() = getArgType(checkNotNull(invokeNode.getInstanceArg()).getType(), arg.getType())

	private fun getArgType(instanceType: ArgType, argType: ArgType): ArgType {
		val resultGeneric = root.getTypeUtils().replaceClassGenerics(instanceType, genericArgType)
		if (resultGeneric != null) {
			return resultGeneric
		}
		return argType
	}

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o == null || javaClass != o.javaClass) {
			return false
		}
		val that = o as TypeBoundInvokeUse
		return invokeNode == that.invokeNode
	}

	override fun hashCode(): Int = invokeNode.hashCode()

	override fun toString(): String = "InvokeAssign{" + invokeNode.callMth.shortId +
		", argType=" + genericArgType +
		", currentType=" + type +
		", instanceArg=" + invokeNode.getInstanceArg() +
		'}'
}
