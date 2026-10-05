package kadx.core.dex.visitors.typeinference

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg

/**
 * 一次类型更新请求：把某个参数改成候选类型。
 *
 * **算法意图**：[TypeUpdate] 把请求放入队列后逐个处理；
 * [direct] 为 true 表示跳过“验证”直接请求更新（用于 SSA 变量级联更新）。
 * [callback] 在结果计算出来后回调，可为空。
 *
 * **Kotlin 转换说明**：保留显式 `getXxx()/isDirect()` 方法名，Java 调用方零改动。
 */
class TypeUpdateRequest(
	val arg: InsnArg,
	val candidateType: ArgType,
	private val direct: Boolean,
	val callback: ITypeUpdateCallback?,
) {

	fun isDirect(): Boolean = direct

	override fun toString(): String = "TypeUpdateRequest{arg=$arg, candidateType=$candidateType}"
}
