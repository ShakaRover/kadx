package jadx.api.plugins.input.data

/**
 * 异常捕获（catch）信息接口：描述一个 try 块对应的 catch 子句。
 *
 * **背景**：Dex / class file 的异常表解析后，每个 try 区间关联一组
 * "异常类型 → 处理指令"的映射，本接口就是这组数据的抽象。
 */
public interface ICatch {

	/** @return 捕获的异常类型名数组（如 ["java.lang.Exception"]）*/
	public val types: Array<String>

	/** @return 各异常类型对应的处理指令偏移数组，与 [getTypes] 一一对应 */
	public val handlers: IntArray

	/**
	 * @return catch-all（捕获所有异常）的处理指令偏移；无 catch-all 时为 -1。
	 * **注意**：这是普通方法而非属性 getter，Kotlin 中保持函数形式。
	 */
	public val catchAllHandler: Int
}
