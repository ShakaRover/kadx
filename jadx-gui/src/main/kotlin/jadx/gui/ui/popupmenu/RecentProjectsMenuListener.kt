package jadx.gui.ui.popupmenu

import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import java.nio.file.Path
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.event.MenuEvent
import javax.swing.event.MenuListener

/**
 * 「最近打开的项目」菜单监听器。
 *
 * **做什么**：菜单每次被展开时重建菜单项，过滤掉当前项目中已经打开的文件，
 * 点击某一项即可打开该历史项目；没有历史记录时显示一条提示项。
 *
 * **线程模型**：`menuSelected` 在 EDT 上触发，直接操作 Swing 组件。
 */
class RecentProjectsMenuListener(private val mainWindow: MainWindow, private val menu: JMenu) : MenuListener {

	override fun menuSelected(menuEvent: MenuEvent) {
		val current: Set<Path> = HashSet(mainWindow.getProject().filePaths)
		val items: List<JMenuItem> = mainWindow.getSettings().recentProjects
			.filter { path -> !current.contains(path) }
			.map { path ->
				val menuItem = JMenuItem(path.toAbsolutePath().toString())
				menuItem.addActionListener { mainWindow.open(listOf(path)) }
				menuItem
			}

		menu.removeAll()
		if (items.isEmpty()) {
			menu.add(JMenuItem(NLS.str("menu.no_recent_projects")))
		} else {
			items.forEach { menu.add(it) }
		}
	}

	override fun menuDeselected(e: MenuEvent) {
		// no op
	}

	override fun menuCanceled(e: MenuEvent) {
		// no op
	}
}
