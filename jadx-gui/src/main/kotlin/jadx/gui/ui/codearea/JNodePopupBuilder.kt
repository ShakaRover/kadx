package jadx.gui.ui.codearea

import jadx.gui.ui.action.JNodeAction
import jadx.gui.ui.action.JadxGuiAction
import jadx.gui.utils.shortcut.ShortcutsController
import javax.swing.JMenu
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuListener

/**
 * 代码区右键菜单的构建器。
 *
 * **做什么**：向 [JPopupMenu] 里添加分隔符、[JNodeAction]、[JadxGuiAction] 与子菜单，
 * 同时把这些动作登记到 [JNodePopupListener]，让它们能感知当前节点。
 *
 * **为什么在添加动作时就绑定快捷键**：
 * 1. 同一 [jadx.gui.ui.action.ActionModel] 可能存在多个动作实例（不同代码区），
 * 而 [ShortcutsController] 只维护一份绑定，必须立即绑定；
 * 2. 快捷键变化时动作会被重建，因此这里无需长期监听。
 */
class JNodePopupBuilder(
	codeArea: CodeArea,
	private val menu: JPopupMenu,
	private val shortcutsController: ShortcutsController,
) {
	private val popupListener = JNodePopupListener(codeArea)

	init {
		menu.addPopupMenuListener(popupListener)
	}

	fun addSeparator() {
		menu.addSeparator()
	}

	fun add(nodeAction: JNodeAction) {
		if (nodeAction.getActionModel() != null) {
			shortcutsController.bindImmediate(nodeAction)
		}
		menu.add(nodeAction)
		popupListener.addActions(nodeAction)
	}

	fun add(action: JadxGuiAction) {
		if (action.getActionModel() != null) {
			shortcutsController.bindImmediate(action)
		}
		menu.add(action)
		if (action is PopupMenuListener) {
			menu.addPopupMenuListener(action)
		}
	}

	fun addSubmenu(name: String, vararg nodeActions: JNodeAction) {
		val submenu = JMenu(name)
		for (nodeAction in nodeActions) {
			if (nodeAction.getActionModel() != null) {
				shortcutsController.bindImmediate(nodeAction)
			}
			submenu.add(nodeAction)
			popupListener.addActions(nodeAction)
		}
		menu.add(submenu)
	}

	fun getMenu(): JPopupMenu = menu
}
