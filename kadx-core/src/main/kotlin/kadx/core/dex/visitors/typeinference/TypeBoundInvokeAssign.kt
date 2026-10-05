package kadx.core.dex.visitors.typeinference

import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.IMethodDetails
import kadx.core.dex.nodes.RootNode

/**
 * 泛型方法调用（invoke）的动态赋值边界：用实例的泛型实参解析返回类型。
 *
 * **算法意图**：`List<String> list; ... list.get(0)` 返回类型声明可能是 `T`，
 * 需要结合实例真实类型（`List<String>`）与声明类类型替换，得到 `String`。
 * 若解析结果是通配符，则取其通配符上界；仍失败时退回方法声明的返回类型。
 *
 * **Kotlin 转换说明**：保留自定义 [equals]/[hashCode]（按 [invokeNode] 判等）；
 * TODO 提示：未来也可依赖参数类型来推导，目前只依赖实例类型。
 */
class TypeBoundInvokeAssign(
	private val root: RootNode,
	private val invokeNode: InvokeNode,
	private val genericReturnType: ArgType,
) : ITypeBoundDynamic {

	override val bound: BoundEnum get() = BoundEnum.ASSIGN

	override fun getType(updateInfo: TypeUpdateInfo): ArgType = getReturnType(updateInfo.getType(instanceArg))

	override val type: ArgType get() = getReturnType(instanceArg.getType())

	private fun getReturnType(instanceType: ArgType): ArgType {
		val mthDeclType: ArgType
		val methodDetails: IMethodDetails? = root.getMethodUtils().getMethodDetails(invokeNode)
		if (methodDetails != null) {
			// 虚调用时，用方法声明所在类的类型来解析泛型
			mthDeclType = methodDetails.methodInfo.declClass.type
		} else {
			mthDeclType = instanceType
		}
		val resultGeneric = root.getTypeUtils().replaceClassGenerics(instanceType, mthDeclType, genericReturnType)
		val result = processResultType(resultGeneric)
		if (result != null) {
			return result
		}
		return invokeNode.callMth.returnType
	}

	private fun processResultType(resultGeneric: ArgType?): ArgType? {
		if (resultGeneric == null) {
			return null
		}
		if (!resultGeneric.isWildcard()) {
			return resultGeneric
		}
		return resultGeneric.getWildcardType()
	}

	private val instanceArg: InsnArg get() = invokeNode.getArg(0)

	override val arg: RegisterArg? get() = invokeNode.result

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o == null || javaClass != o.javaClass) {
			return false
		}
		val that = o as TypeBoundInvokeAssign
		return invokeNode == that.invokeNode
	}

	override fun hashCode(): Int = invokeNode.hashCode()

	override fun toString(): String = "InvokeAssign{" + invokeNode.callMth.shortId +
		", returnType=" + genericReturnType +
		", currentType=" + type +
		", instanceArg=" + instanceArg +
		'}'
}
