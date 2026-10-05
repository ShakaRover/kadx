package kadx.core.dex.trycatch

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType

/**
 * 挂在异常处理器入口块上的属性：保存对应的 [ExceptionHandler]。
 *
 * **用途**：反编译恢复 try-catch 结构时，通过块上的本属性找到该块属于哪个异常处理器。
 *
 * **Kotlin 转换说明**：getter 保持显式函数形式（其他 Kotlin 文件以 `getHandler()`、
 * `getTryBlock()` 形式调用），JVM 表面与 Java 完全一致。
 */
class ExcHandlerAttr(val handler: ExceptionHandler) : IKadxAttribute {

	override val attrType: AType<ExcHandlerAttr> get() = AType.EXC_HANDLER

	val tryBlock: TryCatchBlockAttr? get() = handler.getTryBlock()

	override fun toString(): String = "ExcHandler: $handler"
}
