package kadx.core.dex.instructions.invokedynamic

import kadx.api.plugins.input.data.MethodHandleType
import kadx.core.dex.instructions.InvokeType
import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * invoke-custom 解析的公共工具方法。
 *
 * Kotlin 转换说明：原 Java 静态方法 [convertInvokeType] 用 companion + `@JvmStatic` 平替。
 */
class InvokeCustomUtils {

	companion object {

		/**
		 * 把方法句柄类型映射为 kadx 的调用类型 [InvokeType]。
		 *
		 * - INVOKE_STATIC → STATIC
		 * - INVOKE_INSTANCE → VIRTUAL
		 * - INVOKE_DIRECT / INVOKE_CONSTRUCTOR → DIRECT
		 * - INVOKE_INTERFACE → INTERFACE
		 * - 字段类句柄等不支持的类型抛异常
		 */
		fun convertInvokeType(type: MethodHandleType): InvokeType = when (type) {
			MethodHandleType.INVOKE_STATIC -> InvokeType.STATIC

			MethodHandleType.INVOKE_INSTANCE -> InvokeType.VIRTUAL

			MethodHandleType.INVOKE_DIRECT,
			MethodHandleType.INVOKE_CONSTRUCTOR,
			-> InvokeType.DIRECT

			MethodHandleType.INVOKE_INTERFACE -> InvokeType.INTERFACE

			else -> throw KadxRuntimeException("Unsupported method handle type: $type")
		}
	}
}
