package jadx.api.plugins.input.data.impl

import jadx.api.plugins.input.data.IDebugInfo
import jadx.api.plugins.input.data.ILocalVar

/**
 * 调试信息的默认实现：行号映射 + 局部变量表。
 *
 * @param sourceLineMap 指令偏移 → 源码行号
 * @param localVars 局部变量列表（含参数）
 */
public class DebugInfo(
	private val sourceLineMap: Map<Int, Int>,
	private val localVars: List<ILocalVar>,
) : IDebugInfo {

	override fun getSourceLineMapping(): Map<Int, Int> = sourceLineMap

	override fun getLocalVars(): List<ILocalVar> = localVars

	override fun toString(): String = "DebugInfo{lines=$sourceLineMap, localVars=$localVars}"
}
