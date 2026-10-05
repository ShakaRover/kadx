package kadx.core.utils.exceptions

import kadx.core.dex.nodes.MethodNode

/**
 * 字节码解码阶段的受检异常。
 *
 * **用途**：当指令解码/类型推断无法继续时抛出，携带方法上下文便于定位。
 * 它是受检异常，会出现在 `@Throws(DecodeException::class)` 中。
 *
 * **Kotlin 转换说明**：保留全部构造器并继承 [KadxException]，Java 调用方零改动。
 */
open class DecodeException : KadxException {

	constructor(message: String) : super(message)

	constructor(message: String, cause: Throwable?) : super(message, cause)

	constructor(mth: MethodNode, msg: String) : super(mth, msg, null)

	constructor(mth: MethodNode, msg: String, th: Throwable?) : super(mth, msg, th)

	companion object {
		private const val serialVersionUID: Long = -6611189094923499636L
	}
}
