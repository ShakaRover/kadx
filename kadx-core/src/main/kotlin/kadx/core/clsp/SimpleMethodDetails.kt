package kadx.core.clsp

import kadx.api.plugins.input.data.AccessFlags
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.IMethodDetails

/**
 * 仅由 [MethodInfo] 构建的“简化方法详情”。
 *
 * **使用场景**：当某个方法在 classpath 中找不到对应信息（未知方法）时，
 * 用它作为兜底实现。因为来源只有方法签名，所以泛型参数、throws 列表等
 * 都是未知的，只能返回空列表或默认值。
 *
 * **Kotlin 转换说明**：实现 [IMethodDetails] 接口，成员以 Kotlin 属性形式实现；
 * JVM 上仍生成 `getMethodInfo()` 等访问器，JVM 表面不变。
 */
class SimpleMethodDetails(override val methodInfo: MethodInfo) : IMethodDetails {

	override val returnType: ArgType get() = methodInfo.returnType

	override val argTypes: List<ArgType> get() = methodInfo.argumentsTypes

	/** 未知：兜底实现没有泛型参数信息 */
	override val typeParameters: List<ArgType> get() = emptyList()

	/** 未知：兜底实现没有 throws 信息 */
	override val throws: List<ArgType> get() = emptyList()

	override val isVarArg: Boolean get() = false

	override val rawAccessFlags: Int get() = AccessFlags.PUBLIC

	/** 在接口默认的调试字符串后追加 `(s)`，表示这是简化（Simple）详情 */
	override fun toAttrString(): String = super<IMethodDetails>.toAttrString() + " (s)"

	override fun toString(): String = "SimpleMethodDetails{$methodInfo}"
}
