package kadx.core.dex.trycatch

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.utils.Utils
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
class CatchAttr private constructor(val handlers: List<ExceptionHandler>) : IKadxAttribute {

	companion object {
		/**
		 * 构建 CatchAttr，并按处理器偏移量升序排序。
		 *
		 * 注意：排序会原地修改传入的列表（与原 Java 行为一致）。
		 */
		fun build(handlers: MutableList<ExceptionHandler>): CatchAttr {
			handlers.sortWith(Comparator.comparingInt(ExceptionHandler::handlerOffset))
			return CatchAttr(handlers)
		}
	}

	override val attrType: AType<CatchAttr> get() = AType.EXC_CATCH

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is CatchAttr) {
			return false
		}
		return handlers == other.handlers
	}

	override fun hashCode(): Int = handlers.hashCode()

	override fun toString(): String = "Catch: " + Utils.listToString(handlers)
}
