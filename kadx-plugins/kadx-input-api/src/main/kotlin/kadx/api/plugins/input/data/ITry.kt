package kadx.api.plugins.input.data

/**
 * try 块信息接口：描述一段受异常保护的方法体区间。
 *
 * **背景**：方法代码中的 try-catch-finally 结构，在字节码层面表现为
 * "指令偏移区间 + catch 子句"的组合，本接口就是这一组合的抽象。
 */
public interface ITry {

	/** @return 该 try 区间关联的 [ICatch]（异常类型 → 处理指令映射）*/
	public val catch: ICatch

	/** @return try 区间的起始指令偏移 */
	public val startOffset: Int

	/** @return try 区间的结束指令偏移 */
	public val endOffset: Int
}
