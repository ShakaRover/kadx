package jadx.plugins.input.java.data.attributes.stack

/**
 * 验证器栈帧中单个槽位的值宽度。
 *
 **做什么**：JVM 字节码验证器区分窄值（int/float 引用等，占 1 槽）
 * 和宽值（long/double，占 2 槽），[StackFrame] 用它还原操作数栈布局。
 */
enum class StackValueType {
	NARROW, // int, float, etc
	WIDE, // long, double
}
