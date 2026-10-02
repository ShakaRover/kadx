package jadx.core.utils.exceptions

import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode

/**
 * 代码生成阶段（Java 源码输出）的受检异常。
 *
 * **用途**：当 [jadx.core.codegen.InsnGen]/[jadx.core.codegen.ClassGen] 无法把 IR 转成合法
 * Java 代码时抛出。它是受检异常，会出现在 `@Throws(CodegenException::class)` 中。
 *
 * **Kotlin 转换说明**：保留全部构造器（含带 [ClassNode]/[MethodNode] 上下文的版本），
 * 并保持继承自 [JadxException]，Java 调用方零改动。
 */
open class CodegenException : JadxException {

	constructor(message: String) : super(message)

	constructor(message: String, cause: Throwable?) : super(message, cause)

	constructor(cls: ClassNode, msg: String) : super(cls, msg, null)

	constructor(cls: ClassNode, msg: String, th: Throwable?) : super(cls, msg, th)

	constructor(mth: MethodNode, msg: String) : super(mth, msg, null)

	constructor(mth: MethodNode, msg: String, th: Throwable?) : super(mth, msg, th)

	companion object {
		private const val serialVersionUID: Long = 39344288912966824L
	}
}
