package kadx.gui.ui.menu

import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Action
import javax.swing.JMenuItem

/**
 * 不可见菜单项：用于把某个动作的快捷键注册进菜单，但不占用显示空间。
 *
 * **做什么**：不绘制任何内容，首选尺寸为 0×0。
 */
class HiddenMenuItem(a: Action) : JMenuItem(a) {

	override fun paintComponent(g: Graphics) {
	}

	override fun getPreferredSize(): Dimension = Dimension(0, 0)
}
