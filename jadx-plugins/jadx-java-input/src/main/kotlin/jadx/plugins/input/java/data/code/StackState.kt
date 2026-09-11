package jadx.plugins.input.java.data.code

import jadx.plugins.input.java.data.attributes.stack.StackFrame
import jadx.plugins.input.java.data.attributes.stack.StackValueType
import java.util.Arrays

/**
 * 字节码解码过程中的操作数栈状态。
 *
 **做什么**：用定长数组模拟 JVM 验证器栈（[maxStack] 个槽），支持 push/pop/peek/insert；
 * [copy] 在跳转指令处保存现场，[fillFromFrame] 从 StackMapTable 显式帧恢复。
 *
 **为什么槽位只记宽度不记类型**：class 文件的字节码不带完整类型信息，
 * 解码器只需知道每个值占 1 还是 2 个寄存器（NARROW/WIDE）即可正确分配寄存器号。
 */
// 主构造器为私有的 (pos, stack) 形式（仅供 copy() 使用）；
// 公开的 maxStack 构造器委托给它并初始化空栈数组
class StackState private constructor(
	private var pos: Int,
	private val stack: Array<StackValueType>,
) {

	constructor(maxStack: Int) : this(-1, Array(maxStack) { StackValueType.NARROW })

	fun copy(): StackState = StackState(pos, Arrays.copyOf(stack, stack.size))

	fun fillFromFrame(frame: StackFrame): StackState {
		val stackSize = frame.stackSize
		pos = stackSize - 1
		if (stackSize > 0) {
			System.arraycopy(frame.stackValueTypes, 0, this.stack, 0, stackSize)
		}
		return this
	}

	fun peek(): Int = pos

	fun peekAt(at: Int): Int = pos - at

	fun peekTypeAt(at: Int): StackValueType {
		val p = pos - at
		if (checkStackIndex(p)) {
			return stack[p]
		}
		return StackValueType.NARROW
	}

	fun insert(at: Int, type: StackValueType): Int {
		val p = pos - at
		System.arraycopy(stack, p, stack, p + 1, at)
		stack[p] = type
		pos++
		return p
	}

	fun push(type: StackValueType): Int {
		val p = ++pos
		if (checkStackIndex(p)) {
			stack[p] = type
		}
		return p
	}

	private fun checkStackIndex(p: Int): Boolean = p >= 0 && p < stack.size

	fun pop(): Int = pos--

	fun clear() {
		pos = -1
	}

	override fun toString(): String {
		val size = pos + 1
		val arr: String
		if (size == 0) {
			arr = "empty"
		} else if (size > 0 && size < stack.size) {
			arr = Arrays.toString(Arrays.copyOf(stack, size))
		} else {
			arr = Arrays.toString(stack) + " (max)"
		}
		return "Stack: " + size + ": " + arr
	}
}
