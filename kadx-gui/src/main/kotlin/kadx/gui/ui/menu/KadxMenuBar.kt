package kadx.gui.ui.menu

import javax.swing.JMenu
import javax.swing.JMenuBar

/**
 * kadx 主菜单栏。
 *
 * **做什么**：遍历所有子菜单，对 [KadxMenu] 类型的菜单触发快捷键重载。
 */
class KadxMenuBar : JMenuBar() {

	fun reloadShortcuts() {
		for (i in 0 until menuCount) {
			val menu: JMenu = getMenu(i)
			if (menu is KadxMenu) {
				menu.reloadShortcuts()
			}
		}
	}
}
