package jadx.gui.ui.codearea.sync

import jadx.api.metadata.ICodeAnnotation

/**
 * 表示代码元数据中的一段注解区间：起点和终点各自对应一个注解。
 *
 * **做什么**：把代码元数据里“某个注解的起点和终点”成对保存，供同步逻辑
 * （方法边界、指令偏移区间等）使用。
 *
 * **为什么用 [Map.Entry]**：原 Java 实现没有内置的元组类型，就用 `Map.Entry`
 * 充当“键值对”。这里保持同样的结构，避免改变下游逻辑。
 */
class CodeMetadataRange(
	val start: Map.Entry<Int, ICodeAnnotation>,
	val end: Map.Entry<Int, ICodeAnnotation>,
) {
	override fun toString(): String = "CodeMetadataRange{start=" +
		start.key +
		"->" +
		start.value +
		",end=" +
		end.key +
		"->" +
		end.value +
		"}"
}
