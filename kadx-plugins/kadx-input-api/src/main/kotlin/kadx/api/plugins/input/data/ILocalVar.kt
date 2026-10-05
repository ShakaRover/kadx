package kadx.api.plugins.input.data

/**
 * 局部变量接口：调试信息中一个变量（含方法参数）的元数据。
 *
 * **背景**：Dex 的 local_debug_info / class file 的 LocalVariableTable 条目
 * 解析后封装为本接口的实现，kadx-core 用它还原源码中的变量名与类型。
 */
public interface ILocalVar {

	/** @return 变量名（可能为空字符串）*/
	public val name: String

	/** @return 寄存器编号（Dex 中变量绑定到寄存器）*/
	public val regNum: Int

	/** @return 变量的描述符/类型字符串 */
	public val type: String

	/** @return 泛型签名；无泛型信息时为 null */
	public val signature: String?

	/** @return 变量作用域的起始指令偏移 */
	public val startOffset: Int

	/** @return 变量作用域的结束指令偏移 */
	public val endOffset: Int

	/**
	 * @return 调试信息标记该变量是否为方法参数。
	 * **注意**：原 Java 注释明确说明此标记可能不准确，不应完全信任。
	 */
	public val isMarkedAsParameter: Boolean
}
