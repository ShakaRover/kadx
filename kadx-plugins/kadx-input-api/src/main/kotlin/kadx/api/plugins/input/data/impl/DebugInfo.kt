package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.data.IDebugInfo
import kadx.api.plugins.input.data.ILocalVar

/**
 * 调试信息的默认实现：行号映射 + 局部变量表。
 *
 * @param sourceLineMap 指令偏移 → 源码行号
 * @param localVars 局部变量列表（含参数）
 */
public class DebugInfo(
	private val sourceLineMap: Map<Int, Int>,
	private val localVarsValue: List<ILocalVar>,
) : IDebugInfo {

	override val sourceLineMapping: Map<Int, Int> get() = sourceLineMap

	override val localVars: List<ILocalVar> get() = localVarsValue

	override fun toString(): String = "DebugInfo{lines=$sourceLineMap, localVars=$localVarsValue}"
}
