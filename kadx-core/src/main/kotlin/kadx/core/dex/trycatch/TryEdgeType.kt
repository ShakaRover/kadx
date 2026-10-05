package kadx.core.dex.trycatch

/**
 * try 体出口边的类型。
 *
 * **背景**：try 体在执行过程中可能通过多种方式离开其代码范围，finally 恢复逻辑需要区分
 * 这些出口，才能正确复制 finally 代码块。本枚举即为这些出口分类。
 */
enum class TryEdgeType {
	/** 正常贯穿：所有 try 块逻辑上都已执行完，控制流自然落到 try 之后的块 */
	TRUE_FALLTHROUGH,

	/** 提前退出：在所有 try 块逻辑执行完之前就离开了 try 体（如提前 return/throw） */
	PREMATURE_EXIT,

	/** 从循环内部退出 */
	LOOP_EXIT,

	/** 跳转到异常处理器（catch/finally 块） */
	HANDLER,
}
