package jadx.gui.ui.codearea.sync

import jadx.gui.ui.codearea.SmaliArea

/**
 * “同步到 Smali 代码区”的策略接口。
 *
 * **做什么**：把当前区域的光标位置换算成目标 [SmaliArea] 中的位置并高亮。
 */
interface IToSmaliSyncStrategy {
	/** 同步到 [to]；成功返回 true。 */
	fun syncTo(to: SmaliArea): Boolean
}
