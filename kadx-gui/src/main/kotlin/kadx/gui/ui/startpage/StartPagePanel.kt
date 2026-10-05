package kadx.gui.ui.startpage

import kadx.gui.settings.KadxSettings
import kadx.gui.ui.MainWindow
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.tab.TabbedPane
import kadx.gui.utils.Icons
import kadx.gui.utils.NLS
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.border.TitledBorder

/**
 * 起始页内容面板：提供“打开文件/项目”按钮与最近项目列表。
 *
 * **做什么**：构建起始页 UI；列表支持双击打开、右键菜单打开/移除、悬停高亮移除按钮。
 *
 * **为什么保留 Swing 线程模型**：所有交互都在 EDT 上。
 */
class StartPagePanel(tabbedPane: TabbedPane, node: StartPageNode) : ContentPanel(tabbedPane, node) {

	private val recentListModel: DefaultListModel<RecentProjectItem> = DefaultListModel()
	private val recentList: RecentProjectsJList = RecentProjectsJList(recentListModel)

	init {
		val baseFont = settings.uiFont
		initUi(baseFont)
		fillRecentProjectsList()
	}

	private fun initUi(baseFont: Font) {
		val openFile = JButton(NLS.str("file.open_title"), Icons.OPEN)
		openFile.addActionListener { mainWindow.openFileDialog() }

		val openProject = JButton(NLS.str("file.open_project"), Icons.OPEN_PROJECT)
		openProject.addActionListener { mainWindow.openProjectDialog() }

		val start = JPanel()
		start.border = sectionFrame(NLS.str("start_page.start"), baseFont)
		start.layout = BoxLayout(start, BoxLayout.LINE_AXIS)
		start.add(openFile)
		start.add(Box.createRigidArea(Dimension(10, 0)))
		start.add(openProject)
		start.add(Box.createHorizontalGlue())

		recentList.cellRenderer = RecentProjectListCellRenderer(baseFont)
		recentList.selectionMode = ListSelectionModel.SINGLE_SELECTION

		val scrollPane = JScrollPane(recentList)
		scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
		scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
		scrollPane.preferredSize = Dimension(400, 250)
		scrollPane.border = BorderFactory.createEmptyBorder()

		recentList.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				val index = recentList.locationToIndex(e.point)
				if (index == -1) {
					return
				}

				val item = recentListModel.getElementAt(index)
				if (item == null) {
					return
				}

				val renderer = recentList.cellRenderer as RecentProjectListCellRenderer
				renderer.getListCellRendererComponent(recentList, item, index, false, false)

				val cellBounds = recentList.getCellBounds(index, index)
				if (cellBounds != null) {
					val xInCell = e.x - cellBounds.x
					val yInCell = e.y - cellBounds.y

					val removeIconBounds = renderer.removeIconBounds
					if (removeIconBounds != null && removeIconBounds.contains(xInCell, yInCell)) {
						removeRecentProject(item.getPath())
						return
					}
				}

				if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
					openRecentProject(item.getPath())
				} else if (SwingUtilities.isRightMouseButton(e)) {
					recentList.selectedIndex = index
					showRecentProjectContextMenu(e)
				}
			}
		})

		recentList.addMouseMotionListener(object : MouseMotionAdapter() {
			override fun mouseMoved(e: MouseEvent) {
				val oldHoveredRemoveBtnIndex = hoveredRemoveBtnIndex
				hoveredRemoveBtnIndex = -1

				val currentCellIndex = recentList.locationToIndex(e.point)

				if (currentCellIndex != -1) {
					val item = recentListModel.getElementAt(currentCellIndex)
					val renderer = recentList.cellRenderer as RecentProjectListCellRenderer
					renderer.getListCellRendererComponent(
						recentList,
						item,
						currentCellIndex,
						recentList.isSelectedIndex(currentCellIndex),
						false,
					)

					val cellBounds = recentList.getCellBounds(currentCellIndex, currentCellIndex)
					if (cellBounds != null) {
						val xInCell = e.x - cellBounds.x
						val yInCell = e.y - cellBounds.y

						val removeIconBounds = renderer.removeIconBounds
						if (removeIconBounds != null && removeIconBounds.contains(xInCell, yInCell)) {
							hoveredRemoveBtnIndex = currentCellIndex
						}
					}
				}

				if (oldHoveredRemoveBtnIndex != hoveredRemoveBtnIndex) {
					if (oldHoveredRemoveBtnIndex != -1) {
						val bounds = recentList.getCellBounds(oldHoveredRemoveBtnIndex, oldHoveredRemoveBtnIndex)
						if (bounds != null) {
							recentList.repaint(bounds)
						}
					}
					if (hoveredRemoveBtnIndex != -1) {
						val bounds = recentList.getCellBounds(hoveredRemoveBtnIndex, hoveredRemoveBtnIndex)
						if (bounds != null) {
							recentList.repaint(bounds)
						}
					}
				}
			}
		})

		val recent = JPanel()
		recent.border = sectionFrame(NLS.str("start_page.recent"), baseFont)
		recent.layout = BoxLayout(recent, BoxLayout.PAGE_AXIS)
		recent.add(scrollPane)

		val center = JPanel()
		center.layout = BorderLayout(10, 10)
		center.add(start, BorderLayout.PAGE_START)
		center.add(recent, BorderLayout.CENTER)
		center.maximumSize = Dimension(700, 600)
		center.alignmentX = Component.CENTER_ALIGNMENT

		layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
		border = BorderFactory.createEmptyBorder(50, 50, 50, 50)
		add(Box.createVerticalGlue())
		add(center)
		add(Box.createVerticalGlue())
	}

	private fun fillRecentProjectsList() {
		recentListModel.clear()
		val recentPaths: List<Path> = settings.recentProjects
		for (path in recentPaths) {
			recentListModel.addElement(RecentProjectItem(path))
		}
		recentList.revalidate()
		recentList.repaint()
	}

	private fun openRecentProject(path: Path) {
		mainWindow.open(path)
	}

	private fun removeRecentProject(path: Path) {
		settings.removeRecentProject(path)
		fillRecentProjectsList()
		if (hoveredRemoveBtnIndex != -1 && hoveredRemoveBtnIndex >= recentListModel.size()) {
			hoveredRemoveBtnIndex = -1
		}
	}

	private fun showRecentProjectContextMenu(e: MouseEvent) {
		val popupMenu = JPopupMenu()
		val selectedItem = recentList.selectedValue

		if (selectedItem != null) {
			val openItem = JMenuItem(NLS.str("file.open_project"))
			openItem.addActionListener { openRecentProject(selectedItem.getPath()) }
			popupMenu.add(openItem)

			val removeItem = JMenuItem(NLS.str("start_page.list.delete_recent_project"))
			removeItem.addActionListener { removeRecentProject(selectedItem.getPath()) }
			popupMenu.add(removeItem)
		}

		popupMenu.show(e.component, e.x, e.y)
	}

	override fun loadSettings() {
	}

	/**
	 * 内部类：覆写 `getToolTipText`，按鼠标所在单元格展示不同提示。
	 */
	private class RecentProjectsJList(model: DefaultListModel<RecentProjectItem>) : JList<RecentProjectItem>(model) {
		override fun getToolTipText(event: MouseEvent): String? {
			val index = locationToIndex(event.point)
			if (index == -1) {
				return null
			}

			val item = model.getElementAt(index)
			if (item == null) {
				return null
			}

			val renderer = cellRenderer as RecentProjectListCellRenderer
			renderer.getListCellRendererComponent(this, item, index, isSelectedIndex(index), false)

			val cellBounds = getCellBounds(index, index)
			if (cellBounds != null) {
				val xInCell = event.x - cellBounds.x
				val yInCell = event.y - cellBounds.y

				val removeIconBounds = renderer.removeIconBounds
				if (removeIconBounds != null && removeIconBounds.contains(xInCell, yInCell)) {
					return NLS.str("start_page.list.delete_recent_project.tooltip")
				}
			}
			return item.absolutePath
		}

		companion object {
			private const val serialVersionUID: Long = 1L
		}
	}

	companion object {
		/** 当前鼠标悬停的“移除”按钮所在行索引；-1 表示无。 */
		var hoveredRemoveBtnIndex: Int = -1

		private const val serialVersionUID: Long = 2457805175218770732L

		private fun sectionFrame(title: String, font: Font): Border {
			val titledBorder = BorderFactory.createTitledBorder(title)
			titledBorder.titleFont = font.deriveFont(Font.BOLD, font.size + 1f)
			val spacing: Border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
			return BorderFactory.createCompoundBorder(titledBorder, spacing)
		}
	}
}
