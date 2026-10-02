package jadx.core.dex.trycatch

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.utils.Utils
import java.util.Comparator

/**
 * 挂到指令上的 catch 属性：记录该指令所属 try 块的全部异常处理器。
 *
 * **用途**：DEX 异常表按“地址区间 -> 处理器”描述；反编译时把这些信息聚合为
 * [CatchAttr] 挂到指令/基本块上，后续 try-catch 区域恢复据此判断哪些指令受保护。
 *
 * **相等性**：按处理器列表内容判等（值语义），保留手写 `equals/hashCode`。
 *
 * **Kotlin 转换说明**：原 Java 私有构造器 + 静态工厂 [build]；[build] 放入 companion 并
 * 标注 `@JvmStatic`，Java 调用方仍写 `CatchAttr.build(...)`。
 */
class CatchAttr private constructor(private val handlers: List<ExceptionHandler>) : IJadxAttribute {

	companion object {
		/**
		 * 构建 CatchAttr，并按处理器偏移量升序排序。
		 *
		 * 注意：排序会原地修改传入的列表（与原 Java 行为一致）。
		 */
		@JvmStatic
		fun build(handlers: MutableList<ExceptionHandler>): CatchAttr {
			handlers.sortWith(Comparator.comparingInt(ExceptionHandler::getHandlerOffset))
			return CatchAttr(handlers)
		}
	}

	fun getHandlers(): List<ExceptionHandler> = handlers

	override fun getAttrType(): AType<CatchAttr> = AType.EXC_CATCH

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is CatchAttr) {
			return false
		}
		return getHandlers() == other.getHandlers()
	}

	override fun hashCode(): Int = getHandlers().hashCode()

	override fun toString(): String = "Catch: " + Utils.listToString(getHandlers())
}
