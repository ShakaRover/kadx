package jadx.plugins.input.java.data.attributes.types

import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.stack.StackFrame
import org.jetbrains.annotations.Nullable
import java.util.Collections

/**
 * StackMapTable attribute：JDK7+ 方法字节码的验证器栈帧表。
 *
 **做什么**：保存"字节码偏移 → 栈帧"映射，供 [jadx.plugins.input.java.data.code.CodeDecodeState]
 * 在跳转指令处恢复操作数栈状态（类型推断的基础）。
 */
class StackMapTableAttr(
	/** 偏移 → 栈帧；无表时为空 map */
	private val map: Map<Int, StackFrame>,
) : IJavaAttribute {

	/** @return [offset] 处的显式栈帧；没有则返回 null（由解码器自行推断） */
	@Nullable
	fun getFor(offset: Int): StackFrame? = map[offset]

	companion object {
		/** 空表单例：方法没有 StackMapTable attribute 时使用 */
		val EMPTY: StackMapTableAttr = StackMapTableAttr(Collections.emptyMap())
	}
}
