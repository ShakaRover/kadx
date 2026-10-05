package kadx.gui.device.debugger

/**
 * Smali 调试器（JDWP 通信）专用受检异常。
 *
 * **做什么**：封装调试过程中的各种错误，并携带一个可选的 JDWP 错误码 [errCode]。
 * 没有错误码时约定为 `-1`；有错误码时调用方可用
 * [SmaliDebugger.errIsTypeMismatched] 等辅助方法判断错误类型。
 *
 * **为什么继承 `Exception`**：原 Java 类继承 `Exception`，大量调用点使用
 * `@Throws` / `throws` 传播，继承层级必须保持。
 */
class SmaliDebuggerException : Exception {
	/** JDWP 错误码；无错误码时为 -1。 */
	private val errCode: Int

	/** 由其它异常包装而来，错误码为 -1。 */
	constructor(e: Exception) : super(e) {
		errCode = -1
	}

	/** 仅携带错误信息，错误码为 -1。 */
	constructor(msg: String) : super(msg) {
		errCode = -1
	}

	/** 同时携带错误信息与原因异常，错误码为 -1。 */
	constructor(msg: String, e: Exception) : super(msg, e) {
		errCode = -1
	}

	/** 携带错误信息与明确的 JDWP 错误码。 */
	constructor(msg: String, errCode: Int) : super(msg) {
		this.errCode = errCode
	}

	/** @return JDWP 错误码（无则 -1） */
	fun getErrCode(): Int = errCode

	companion object {
		private const val serialVersionUID: Long = -1111111202102191403L
	}
}
