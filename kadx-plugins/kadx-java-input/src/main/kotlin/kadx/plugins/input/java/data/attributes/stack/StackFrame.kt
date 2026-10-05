package kadx.plugins.input.java.data.attributes.stack

/**
 * StackMapTable 中的单个验证器栈帧。
 *
 **做什么**：记录某字节码偏移处的操作数栈与局部变量表布局（槽位数量 + 每个槽位的宽度），
 * [kadx.plugins.input.java.data.code.CodeDecodeState] 在跳转指令处用它恢复/校正类型推断状态。
 *
 **为什么 offset/type 不可变而其余可变**：帧解析分两步——先按帧头确定偏移与种类，
 * 再按种类读取栈/局部变量细节，所以后三者由 [StackMapTableReader] 填充。
 */
class StackFrame(
	/** 帧对应的字节码偏移（相对 code 区起点） */
	val offset: Int,
	/** 帧种类（决定后续字段如何解读） */
	val type: StackFrameType,
) {

	/** 操作数栈槽位数（解析前为 0） */
	var stackSize: Int = 0

	/** 每个栈槽的宽度；未填充时为 null */
	var stackValueTypes: Array<StackValueType>? = null

	/** 局部变量表槽位数（解析前为 0） */
	var localsCount: Int = 0
}
