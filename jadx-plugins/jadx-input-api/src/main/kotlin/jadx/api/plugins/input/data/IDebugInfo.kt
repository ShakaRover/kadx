package jadx.api.plugins.input.data

/**
 * 调试信息接口：方法体的行号映射与局部变量表。
 *
 * **背景**：Dex 的 debug_info / class file 的 LocalVariableTable + LineNumberTable
 * 解析后封装为本接口的实现，供 jadx-core 还原源码行号与变量名。
 */
public interface IDebugInfo {

	/** @return 指令偏移 → 源码行号的映射 */
	public val sourceLineMapping: Map<Int, Int>

	/** @return 局部变量列表（含参数，见 [ILocalVar]）*/
	public val localVars: List<ILocalVar>
}
