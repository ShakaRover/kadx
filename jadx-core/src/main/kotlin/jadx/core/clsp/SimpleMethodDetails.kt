package jadx.core.clsp

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.IMethodDetails

/**
 * 仅由 [MethodInfo] 构建的“简化方法详情”。
 *
 * **使用场景**：当某个方法在 classpath 中找不到对应信息（未知方法）时，
 * 用它作为兜底实现。因为来源只有方法签名，所以泛型参数、throws 列表等
 * 都是未知的，只能返回空列表或默认值。
 *
 * **Kotlin 转换说明**：实现 [IMethodDetails] 接口，所有 getter 必须显式 `override fun`；
 * 字段为 `private val`，通过同名 getter 暴露，JVM 表面不变。
 */
class SimpleMethodDetails(private val methodInfo: MethodInfo) : IMethodDetails {

	override fun getMethodInfo(): MethodInfo = methodInfo

	override fun getReturnType(): ArgType = methodInfo.returnType

	override fun getArgTypes(): List<ArgType> = methodInfo.argumentsTypes

	/** 未知：兜底实现没有泛型参数信息 */
	override fun getTypeParameters(): List<ArgType> = emptyList()

	/** 未知：兜底实现没有 throws 信息 */
	override fun getThrows(): List<ArgType> = emptyList()

	override fun isVarArg(): Boolean = false

	override fun getRawAccessFlags(): Int = AccessFlags.PUBLIC

	/** 在接口默认的调试字符串后追加 `(s)`，表示这是简化（Simple）详情 */
	override fun toAttrString(): String = super<IMethodDetails>.toAttrString() + " (s)"

	override fun toString(): String = "SimpleMethodDetails{$methodInfo}"
}
