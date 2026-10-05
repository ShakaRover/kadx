package kadx.gui.ui.menu

import kadx.gui.ui.action.ActionModel
import kadx.gui.ui.action.KadxGuiAction
import kadx.gui.utils.shortcut.Shortcut
import kadx.gui.utils.shortcut.ShortcutsController
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuItem

/**
 * kadx 菜单：在添加菜单项时自动把动作绑定到快捷键控制器。
 *
 * **做什么**：覆写 `add` 系列方法，对 [KadxGuiAction] 补上“快捷键占位组件”并注册绑定；
 * [reloadShortcuts] 在快捷键设置变化后刷新每个菜单项的加速键。
 *
 * **为什么用 [KADX_MENU_COMPONENT]**：Swing 动作需要一个组件来承载快捷键，
 * 这里用占位组件避免依赖真实菜单项的可见性。
 */
class KadxMenu(name: String, private val shortcutsController: ShortcutsController) : JMenu(name) {

	override fun add(menuItem: JMenuItem): JMenuItem {
		val action = menuItem.action
		bindAction(action)
		return super.add(menuItem)
	}

	override fun add(action: Action): JMenuItem {
		bindAction(action)
		return super.add(action)
	}

	fun bindAction(action: Action?) {
		if (action is KadxGuiAction) {
			val shortcutComponent = action.getShortcutComponent()
			if (shortcutComponent == null) {
				action.setShortcutComponent(KADX_MENU_COMPONENT)
			}
			shortcutsController.bind(action)
		}
	}

	fun reloadShortcuts() {
		for (i in 0 until itemCount) {
			// TODO 目前整菜单重绘，后续可只重绘快捷键发生变化的项
			val item = getItem(i) ?: continue

			val action = item.action
			if (action !is KadxGuiAction || action.getActionModel() == null) {
				continue
			}

			val actionModel: ActionModel = action.getActionModel() ?: continue
			val shortcut: Shortcut? = shortcutsController.get(actionModel)
			if (shortcut != null) {
				item.accelerator = shortcut.toKeyStroke()
			} else {
				item.accelerator = null
			}
			item.repaint()
			item.revalidate()
		}
	}

	companion object {
		/** 用于填充动作快捷键组件属性的占位组件。 */
		val KADX_MENU_COMPONENT: JComponent = object : JComponent() {
			override fun toString(): String = "KADX_MENU_COMPONENT"
		}
	}
}
