package kadx.gui.ui.tab

import kadx.core.utils.ListUtils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JEditableNode
import kadx.gui.treemodel.JNode
import kadx.gui.ui.MainWindow
import kadx.gui.ui.action.ActionModel
import kadx.gui.ui.action.KadxGuiAction
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.tab.dnd.TabDndGestureListener
import kadx.gui.utils.Icons
import kadx.gui.utils.NLS
import kadx.gui.utils.OverlayIcon
import kadx.gui.utils.UiUtils
import kadx.gui.utils.ui.NodeLabel
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.DragGestureListener
import java.awt.dnd.DragSource
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicButtonUI

/**
 * 单个标签页的标题组件（图标 + 标题 + 置顶/关闭按钮）。
 *
 * **做什么**：负责标签标题的显示、置顶/书签/预览状态的刷新、右键菜单，
 * 以及为标签注册拖拽手势识别器。
 *
 * **线程模型**：全部在 EDT 上运行，保持原 Swing 实现不变。
 *
 * **为什么不是 `data class`**：这是有状态的 Swing 组件。
 */
class TabComponent(
	private val tabbedPane: TabbedPane,
	private val contentPanel: ContentPanel,
) : JPanel() {

	private val tabsController: TabsController = tabbedPane.getMainWindow().getTabsController()

	private lateinit var icon: OverlayIcon
	private lateinit var label: JLabel
	private lateinit var pinBtn: JButton
	private lateinit var closeBtn: JButton

	init {
		initUi()
	}

	fun loadSettings() {
		label.setFont(labelFont)
		val dnd = tabbedPane.getDnd()
		if (dnd != null) {
			dnd.loadSettings()
		}
	}

	private val labelFont: Font get() {
		val font = tabsController.getMainWindow().getSettings().codeFont
		var style = font.getStyle()
		style = style or Font.BOLD
		if (blueprint.isPreviewTab) {
			style = style xor Font.ITALIC // 翻转斜体位以区分预览标签
		}
		return font.deriveFont(style)
	}

	private fun initUi() {
		layout = FlowLayout(FlowLayout.LEFT, 0, 0)
		isOpaque = false

		val node = getNode()
		icon = OverlayIcon(checkNotNull(node.getIcon()))

		label = NodeLabel(buildTabTitle(node), node.disableHtml())
		val toolTip = contentPanel.getNode().getTooltip()
		if (toolTip != null) {
			toolTipText = toolTip
		}
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10))
		label.setIcon(icon)
		if (node is JEditableNode) {
			node.addChangeListener { label.setText(buildTabTitle(node)) }
		}

		pinBtn = JButton()
		pinBtn.setIcon(Icons.PIN)
		pinBtn.setRolloverIcon(Icons.PIN_HOVERED)
		pinBtn.setRolloverEnabled(true)
		pinBtn.setOpaque(false)
		pinBtn.setUI(BasicButtonUI())
		pinBtn.setContentAreaFilled(false)
		pinBtn.setBorder(null)
		pinBtn.setBorderPainted(false)
		pinBtn.addActionListener { togglePin() }

		closeBtn = JButton()
		closeBtn.setIcon(Icons.CLOSE_INACTIVE)
		closeBtn.setRolloverIcon(Icons.CLOSE)
		closeBtn.setRolloverEnabled(true)
		closeBtn.setOpaque(false)
		closeBtn.setUI(BasicButtonUI())
		closeBtn.setContentAreaFilled(false)
		closeBtn.setFocusable(false)
		closeBtn.setBorder(null)
		closeBtn.setBorderPainted(false)
		closeBtn.addActionListener {
			tabsController.closeTab(node, true)
		}

		val clickAdapter = object : MouseAdapter() {
			override fun mousePressed(e: MouseEvent) {
				if (SwingUtilities.isMiddleMouseButton(e)) {
					tabsController.closeTab(node, true)
				} else if (SwingUtilities.isRightMouseButton(e)) {
					val menu = createTabPopupMenu()
					menu.show(e.getComponent(), e.getX(), e.getY())
				} else if (SwingUtilities.isLeftMouseButton(e)) {
					tabsController.selectTab(node)
					if (e.getClickCount() == 2) {
						tabsController.setTabPreview(node, false)
					}
				}
			}
		}
		addMouseListener(clickAdapter)
		addListenerForDnd()

		add(label)
		border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

		update()
	}

	fun update() {
		updateCloseOrPinButton()
		updateBookmarkIcon()
		updateFont()
	}

	private fun updateCloseOrPinButton() {
		if (blueprint.isPinned) {
			if (closeBtn.isShowing) {
				remove(closeBtn)
			}
			if (!pinBtn.isShowing) {
				add(pinBtn)
			}
		} else {
			if (pinBtn.isShowing) {
				remove(pinBtn)
			}
			if (!closeBtn.isShowing) {
				add(closeBtn)
			}
		}
	}

	private fun updateBookmarkIcon() {
		icon.clear()
		if (blueprint.isBookmarked) {
			icon.add(Icons.BOOKMARK_OVERLAY_DARK)
		}
		label.repaint()
	}

	private fun togglePin() {
		val pinned = !blueprint.isPinned
		tabsController.setTabPinned(getNode(), pinned)

		if (pinned) {
			tabsController.setTabPositionFirst(getNode())
		}
	}

	private fun toggleBookmark() {
		val bookmarked = !blueprint.isBookmarked
		tabsController.setTabBookmarked(getNode(), bookmarked)
	}

	private fun updateFont() {
		label.setFont(labelFont)
	}

	private fun addListenerForDnd() {
		val dnd = tabbedPane.getDnd() ?: return
		val comp = this
		val dgl: DragGestureListener = object : TabDndGestureListener(dnd) {
			override fun getDragOrigin(e: DragGestureEvent): Point = SwingUtilities.convertPoint(comp, e.getDragOrigin(), tabbedPane)
		}
		DragSource.getDefaultDragSource()
			.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, dgl)
	}

	private fun buildTabTitle(node: JNode): String {
		var tabTitle = node.makeStringHtml()
		if (tabbedPane.tabWithTitleExists(tabTitle)) {
			tabTitle = node.makeLongString()
		}
		val newTabTitle = UiUtils.limitStringLength(tabTitle, TAB_TITLE_MAX_LENGTH)
		if (newTabTitle != tabTitle) {
			if (tabbedPane.tabWithTitleExists(newTabTitle)) {
				// 较短版本也已存在 => 改用较长版本（最后一次尝试）
				tabTitle = UiUtils.limitStringLength(tabTitle, (TAB_TITLE_MAX_LENGTH * 1.2).toInt())
			} else {
				tabTitle = newTabTitle
			}
		}
		if (node is JEditableNode) {
			if (node.isChanged) {
				return "*$tabTitle"
			}
		}
		return tabTitle
	}

	private fun createTabPopupMenu(): JPopupMenu {
		val menu = JPopupMenu()

		val nodeFullName = getNodeFullName(contentPanel)
		if (nodeFullName != null) {
			val copyRootClassName = JMenuItem(NLS.str("tabs.copy_class_name"))
			copyRootClassName.addActionListener { UiUtils.setClipboardString(nodeFullName) }
			menu.add(copyRootClassName)
			menu.addSeparator()
		}

		if (blueprint.supportsQuickTabs()) {
			val pinTitle = if (blueprint.isPinned) NLS.str("tabs.unpin") else NLS.str("tabs.pin")
			val pinTab = JMenuItem(pinTitle)
			pinTab.addActionListener { togglePin() }
			menu.add(pinTab)

			val unpinAll = JMenuItem(NLS.str("tabs.unpin_all"))
			unpinAll.addActionListener { tabsController.unpinAllTabs() }
			menu.add(unpinAll)

			val bookmarkTitle = if (blueprint.isBookmarked) NLS.str("tabs.unbookmark") else NLS.str("tabs.bookmark")
			val bookmarkTab = JMenuItem(bookmarkTitle)
			bookmarkTab.addActionListener { toggleBookmark() }
			menu.add(bookmarkTab)

			val unbookmarkAll = JMenuItem(NLS.str("tabs.unbookmark_all"))
			unbookmarkAll.addActionListener { tabsController.unbookmarkAllTabs() }
			menu.add(unbookmarkAll)
			menu.addSeparator()
		}

		if (nodeFullName != null) {
			val mainWindow = tabsController.getMainWindow()
			val selectInTree = KadxGuiAction(ActionModel.SYNC, Runnable { mainWindow.selectNodeInTree(getNode()) })
			// 只附加快捷键而不绑定，用于展示当前键位
			selectInTree.setShortcut(mainWindow.getShortcutsController().get(ActionModel.SYNC))
			menu.add(selectInTree)
			menu.addSeparator()
		}

		val closeTab = JMenuItem(NLS.str("tabs.close"))
		closeTab.addActionListener { tabsController.closeTab(getNode(), true) }
		if (blueprint.isPinned) {
			closeTab.setEnabled(false)
		}
		menu.add(closeTab)

		val tabs = tabsController.openTabs
		if (tabs.size > 1) {
			val closeOther = JMenuItem(NLS.str("tabs.closeOthers"))
			closeOther.addActionListener {
				val currentNode = getNode()
				for (tab in tabs) {
					if (tab.node !== currentNode) {
						tabsController.closeTab(tab, true)
					}
				}
			}
			menu.add(closeOther)

			val closeAll = JMenuItem(NLS.str("tabs.closeAll"))
			closeAll.addActionListener { tabsController.closeAllTabs(true) }
			menu.add(closeAll)

			// 这里不用 TabsController，因为标签位置是 TabbedPane 特有的
			val contentPanels = tabbedPane.tabs
			val currentIndex = contentPanels.indexOf(contentPanel)
			if (currentIndex > 0) { // 仅当左侧还有标签时才添加（index > 0）
				val closeAllLeft = JMenuItem(NLS.str("tabs.closeAllLeft"))
				closeAllLeft.addActionListener {
					// 从当前标签前一个开始，逆序关闭到最左侧
					for (i in currentIndex - 1 downTo 0) {
						val panelToClose = contentPanels[i]
						tabsController.closeTab(panelToClose.getNode(), true)
					}
				}
				menu.add(closeAllLeft)
			}
			if (contentPanel !== ListUtils.last(contentPanels)) {
				val closeAllRight = JMenuItem(NLS.str("tabs.closeAllRight"))
				closeAllRight.addActionListener {
					var pastCurrentPanel = false
					for (panel in contentPanels) {
						if (!pastCurrentPanel) {
							if (panel === contentPanel) {
								pastCurrentPanel = true
							}
						} else {
							tabsController.closeTab(panel.getNode(), true)
						}
					}
				}
				menu.add(closeAllRight)
			}
			menu.addSeparator()

			val selectedTab = tabsController.getSelectedTab()
			for (tab in tabs) {
				if (tab === selectedTab) {
					continue
				}
				val node = tab.node
				val clsName = node.makeLongString()
				val item = JMenuItem(clsName)
				item.addActionListener { tabsController.codeJump(node) }
				item.setIcon(node.getIcon())
				menu.add(item)
			}
		}
		return menu
	}

	private fun getNodeFullName(contentPanel: ContentPanel): String? {
		val node = contentPanel.getNode()
		val jClass = node.getRootClass()
		if (jClass != null) {
			return jClass.fullName
		}
		return node.getName()
	}

	fun getContentPanel(): ContentPanel = contentPanel

	val blueprint: TabBlueprint get() {
		val node = contentPanel.getNode()
		return tabsController.getTabByNode(node)
			?: throw KadxRuntimeException("TabComponent does not have a corresponding TabBlueprint, node: $node")
	}

	fun getNode(): JNode = contentPanel.getNode()

	fun getTabTitle(): String = label.getText()

	companion object {
		private const val serialVersionUID = -8147035487543610321L

		private const val TAB_TITLE_MAX_LENGTH = 30
	}
}
