package jadx.core.utils.exceptions

import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.ErrorsCounter

/**
 * jadx 通用受检异常基类。
 *
 * **用途**：反编译过程中可预期的失败（如无法解析字节码、代码生成失败）统一抛出该异常。
 * 它继承自 [Exception]（受检异常），因此会出现在 `@Throws(JadxException::class)` 中，
 * 供 Java 调用方按需捕获。
 *
 * **Kotlin 转换说明**：
 * - 保留全部 4 个构造器，保证 Java 侧 `new JadxException(...)` 调用零改动；
 * - 类声明为 `open`，因为 [CodegenException]、[DecodeException] 继承它；
 * - [serialVersionUID] 用 `const val` 保留 Java 序列化所需的静态字段。
 */
open class JadxException : Exception {

	constructor(message: String) : super(message)

	constructor(message: String, cause: Throwable?) : super(message, cause)

	constructor(cls: ClassNode, msg: String, th: Throwable?) : super(ErrorsCounter.formatMsg(cls, msg), th)

	constructor(mth: MethodNode, msg: String, th: Throwable?) : super(ErrorsCounter.formatMsg(mth, msg), th)

	companion object {
		private const val serialVersionUID: Long = 3577449089978463557L
	}
}
