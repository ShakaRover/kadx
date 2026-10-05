package kadx.gui.utils.ui

import javax.swing.JMenuItem

/**
 * 绑定 [Runnable] 的简单菜单项。
 *
 * **做什么**：创建带文本的 [JMenuItem]，点击时执行传入的 [Runnable]。
 */
class SimpleMenuItem(text: String, action: Runnable) : JMenuItem(text) {

	init {
		addActionListener { action.run() }
	}
}
