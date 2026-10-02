package jadx.core.dex.trycatch

import jadx.core.dex.nodes.BlockNode
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.Objects
import java.util.Optional

/**
 * try 体的一条出口边：表示从 try 体内的一个块离开到目标块。
 *
 * **为什么需要它**：finally 代码复制需要知道 try 体有哪些出口、每个出口的类型
 * （正常贯穿 / 提前退出 / 循环退出 / 跳异常处理器），本类即为这些出口的统一表示。
 *
 * **一致性约束**：类型为 [TryEdgeType.HANDLER] 时必须携带异常处理器；
 * 其他类型则不能携带。构造时即校验，违反则抛出 [JadxRuntimeException]。
 *
 * **Kotlin 转换说明**：这是图节点，必须保留身份/值语义（原 Java 手写 `equals/hashCode`），
 * 不能用 data class。`handler` 字段为私有，通过显式 `getExceptionHandler()` 暴露
 * （不生成多余的 `getHandler()`）。Java 的 `==` 引用比较改写为 `===`。
 */
class TryEdge(
	val source: BlockNode,
	val target: BlockNode,
	val type: TryEdgeType,
	private val handler: Optional<ExceptionHandler>,
) {

	constructor(source: BlockNode, target: BlockNode, type: TryEdgeType) :
		this(source, target, type, Optional.empty())

	constructor(source: BlockNode, target: BlockNode, handler: ExceptionHandler) :
		this(source, target, TryEdgeType.HANDLER, Optional.of(handler))

	init {
		if (isHandlerExit() && handler.isEmpty()) {
			throw JadxRuntimeException(
				"Attempted to add a null exception handler as an edge of \"$type\" type",
			)
		} else if (isNotHandlerExit() && handler.isPresent()) {
			throw JadxRuntimeException(
				"Attempted to add an exception handler as an edge of \"$type\" type",
			)
		}
	}

	override fun toString(): String {
		val sb = StringBuilder("TryEdge: [")
		sb.append(type)
		sb.append(' ')
		sb.append(source.toString())
		sb.append(" -> ")
		sb.append(target.toString())
		sb.append("] - Handler: ")
		if (handler.isEmpty()) {
			sb.append("None")
		} else {
			sb.append(handler.get().toString())
		}
		return sb.toString()
	}

	override fun equals(other: Any?): Boolean {
		if (other !is TryEdge) {
			return false
		}
		return source == other.source &&
			target == other.target &&
			handler == other.handler &&
			type == other.type
	}

	override fun hashCode(): Int = Objects.hash(source, target, type, handler)

	fun isHandlerExit(): Boolean = type === TryEdgeType.HANDLER

	fun isNotHandlerExit(): Boolean = !isHandlerExit()

	fun getExceptionHandler(): ExceptionHandler {
		if (!isHandlerExit()) {
			throw JadxRuntimeException("Attempted to get the exception handler of a non-handler edge type")
		}
		if (handler.isEmpty()) {
			throw JadxRuntimeException(
				"Attempted to get the exception handler of a handler edge type, however none was present",
			)
		}
		return handler.get()
	}
}
