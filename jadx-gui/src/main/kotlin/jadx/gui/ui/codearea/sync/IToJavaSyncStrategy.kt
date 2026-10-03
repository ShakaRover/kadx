package jadx.gui.ui.codearea.sync

import jadx.gui.ui.codearea.CodeArea

/**
 * “同步到 Java 代码区”的策略接口。
 *
 * **做什么**：把当前区域的光标位置换算成目标 [CodeArea] 中的位置并高亮。
 */
interface IToJavaSyncStrategy {
	/** 同步到 [to]；成功返回 true。 */
	fun syncTo(to: CodeArea): Boolean
}
