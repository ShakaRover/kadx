package jadx.core.clsp

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.utils.Utils

/**
 * classpath 图中的方法节点：保存从 jar 包（或 .jcst 文件）解析出的方法信息。
 *
 * **与 [MethodInfo] 的区别**：`MethodInfo` 只描述“方法是谁、签名是什么”；
 * `ClspMethod` 额外携带泛型参数、throws 列表与访问标志等完整信息，
 * 供类型推导 / 泛型恢复等 Pass 使用。
 *
 * **Kotlin 转换说明**：本类实现 [IMethodDetails] 接口，接口成员以 Kotlin 属性形式实现，
 * JVM 上仍生成 `getMethodInfo()` 等访问器。字段一律为 `private val`/`override val`，
 * JVM 表面与 Java 完全一致。
 * 保留手写的 `equals/hashCode`（按 [MethodInfo] 判等），不使用 data class。
 */
class ClspMethod(
	override val methodInfo: MethodInfo,
	override val argTypes: List<ArgType>,
	override val returnType: ArgType,
	override val typeParameters: List<ArgType>,
	override val throws: List<ArgType>,
	private val accFlags: Int,
) : IMethodDetails,
	Comparable<ClspMethod> {

	/**
	 * 判断本方法是否带有泛型实参。
	 *
	 * 做法：把解析出的 [argTypes] 与方法签名原始参数类型 [MethodInfo.getArgumentsTypes] 比较，
	 * 若不同说明 classpath 里保存的是泛型版本（如 `List<String>` 而非 `List`）。
	 */
	fun containsGenericArgs(): Boolean = argTypes != methodInfo.argumentsTypes

	/** 参数个数 */
	val argsCount: Int get() = argTypes.size

	/** 是否为可变参数方法（DEX/Java 的 VARARGS 标志位） */
	override val isVarArg: Boolean get() = (accFlags and AccessFlags.VARARGS) != 0

	override val rawAccessFlags: Int get() = accFlags

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is ClspMethod) {
			return false
		}
		return methodInfo == other.methodInfo
	}

	override fun hashCode(): Int = methodInfo.hashCode()

	override fun compareTo(other: ClspMethod): Int = methodInfo.compareTo(other.methodInfo)

	/** 在接口默认的调试字符串后追加 `(c)`，表示来自 classpath */
	override fun toAttrString(): String = super<IMethodDetails>.toAttrString() + " (c)"

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("ClspMth{")
		if (Utils.notEmpty(typeParameters)) {
			sb.append('<')
			sb.append(Utils.listToString(typeParameters))
			sb.append("> ")
		}
		sb.append(methodInfo.fullName)
		sb.append('(')
		sb.append(Utils.listToString(argTypes))
		sb.append("):")
		sb.append(returnType)
		if (isVarArg) {
			sb.append(" VARARG")
		}
		val throwsList = throws
		if (Utils.notEmpty(throwsList)) {
			sb.append(" throws ").append(Utils.listToString(throwsList))
		}
		sb.append('}')
		return sb.toString()
	}
}
