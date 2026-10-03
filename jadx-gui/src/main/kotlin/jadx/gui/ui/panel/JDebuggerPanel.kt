package jadx.gui.ui.panel

import jadx.core.utils.StringUtils
import jadx.gui.device.debugger.DebugController
import jadx.gui.device.protocol.ADBDevice
import jadx.gui.treemodel.JClass
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.SmaliArea
import jadx.gui.ui.dialog.ADBDialog
import jadx.gui.ui.popupmenu.VarTreePopupMenu
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Label
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JToolBar
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * 调试器面板：堆栈帧、线程、变量树、日志与 Logcat 的组合视图。
 *
 * **做什么**：左侧显示线程/堆栈帧，右侧显示变量树与日志；顶部工具栏提供重跑、停止、
 * 运行/暂停、单步等按钮；同时注册 F7/F8/F9 等调试快捷键。
 *
 * **线程模型**：所有 UI 更新通过 `SwingUtilities.invokeLater`
 * 或 [UiUtils.uiRun] 投递到 EDT。
 */
class JDebuggerPanel(private val mainWindow: MainWindow) : JPanel() {

	private val stackFrameList: JList<IListElement>
	private val threadBox: JComboBox<IListElement>
	private val logger: JTextArea
	private val variableTree: JTree
	private val variableTreeModel: DefaultTreeModel
	private val rootTreeNode: DefaultMutableTreeNode
	private val thisTreeNode: DefaultMutableTreeNode
	private val regTreeNode: DefaultMutableTreeNode

	private val rightSplitter: JSplitPane
	private val leftSplitter: JSplitPane
	private val controller: IDebugController
	private val logcatPanel: LogcatPanel

	private val varTreeMenu: VarTreePopupMenu
	private var controllerShortCutDispatcher: KeyEventDispatcher? = null

	init {
		UiUtils.uiThreadGuard()
		controller = DebugController()
		this.layout = BorderLayout()
		this.minimumSize = Dimension(100, 150)

		leftSplitter = JSplitPane()
		rightSplitter = JSplitPane()

		leftSplitter.setDividerLocation(mainWindow.getSettings().debuggerStackFrameSplitterLoc)
		rightSplitter.setDividerLocation(mainWindow.getSettings().debuggerVarTreeSplitterLoc)

		val stackFramePanel = JPanel(BorderLayout())
		threadBox = JComboBox()
		stackFrameList = JList()
		threadBox.setModel(DefaultComboBoxModel<IListElement>())
		stackFrameList.setModel(DefaultListModel<IListElement>())

		stackFramePanel.add(threadBox, BorderLayout.NORTH)
		stackFramePanel.add(JScrollPane(stackFrameList), BorderLayout.CENTER)

		val variablePanel = JPanel(CardLayout())
		variableTree = JTree()
		variablePanel.add(JScrollPane(variableTree))

		rootTreeNode = DefaultMutableTreeNode()
		thisTreeNode = DefaultMutableTreeNode("this")
		regTreeNode = DefaultMutableTreeNode("var")
		rootTreeNode.add(thisTreeNode)
		rootTreeNode.add(regTreeNode)
		variableTreeModel = DefaultTreeModel(rootTreeNode)
		variableTree.setModel(variableTreeModel)
		variableTree.expandPath(TreePath(rootTreeNode.getPath()))
		variableTree.setCellRenderer(object : DefaultTreeCellRenderer() {
			override fun getTreeCellRendererComponent(
				tree: JTree,
				value: Any?,
				sel: Boolean,
				expanded: Boolean,
				leaf: Boolean,
				row: Int,
				hasFocus: Boolean,
			): Component {
				val c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
				if (value is ValueTreeNode) {
					if (value.isUpdated) {
						setForeground(Color.RED)
					}
				}
				return c
			}
		})

		varTreeMenu = VarTreePopupMenu(mainWindow)

		val loggerPanel = JTabbedPane()
		logger = JTextArea()
		logger.setEditable(false)
		logger.setLineWrap(true)
		val loggerScroll = JScrollPane(logger)
		loggerPanel.addTab("Debugger Log", null, loggerScroll, null)
		this.logcatPanel = LogcatPanel(this)
		loggerPanel.addTab(NLS.str("logcat.logcat"), null, logcatPanel, null)

		leftSplitter.setLeftComponent(stackFramePanel)
		leftSplitter.setRightComponent(rightSplitter)
		leftSplitter.setResizeWeight(MainWindow.SPLIT_PANE_RESIZE_WEIGHT)

		rightSplitter.setLeftComponent(variablePanel)
		rightSplitter.setRightComponent(loggerPanel)
		rightSplitter.setResizeWeight(MainWindow.SPLIT_PANE_RESIZE_WEIGHT)

		val headerPanel = JPanel(BorderLayout())
		headerPanel.add(Label(), BorderLayout.WEST)
		headerPanel.add(initToolBar(), BorderLayout.CENTER)
		val closeBtn = JButton(UiUtils.openSvgIcon("ui/close"))
		closeBtn.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (controller.isDebugging()) {
					val what = JOptionPane.showConfirmDialog(
						mainWindow,
						NLS.str("debugger.cfm_dialog_msg"),
						NLS.str("debugger.cfm_dialog_title"),
						JOptionPane.OK_CANCEL_OPTION,
					)
					if (what == JOptionPane.OK_OPTION) {
						controller.exit()
						logcatPanel.exit()
					} else {
						return
					}
				} else {
					mainWindow.destroyDebuggerPanel()
				}
				unregShortcuts()
			}
		})
		headerPanel.add(closeBtn, BorderLayout.EAST)

		this.add(headerPanel, BorderLayout.NORTH)
		this.add(leftSplitter, BorderLayout.CENTER)
		listenUIEvents()
	}

	fun getMainWindow(): MainWindow = mainWindow

	private fun listenUIEvents() {
		stackFrameList.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (e.getClickCount() % 2 == 0) {
					stackFrameSelected(e.getPoint())
				}
			}
		})
		variableTree.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (SwingUtilities.isRightMouseButton(e)) {
					treeNodeRightClicked(e)
				}
			}
		})
	}

	private fun initToolBar(): JToolBar {
		val stepOver = object : AbstractAction(NLS.str("debugger.step_over"), ICON_STEP_OVER) {
			override fun actionPerformed(e: ActionEvent) {
				controller.stepOver()
			}
		}
		stepOver.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.step_over"))

		val stepInto = object : AbstractAction(NLS.str("debugger.step_into"), ICON_STEP_INTO) {
			override fun actionPerformed(e: ActionEvent) {
				controller.stepInto()
			}
		}
		stepInto.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.step_into"))

		val stepOut = object : AbstractAction(NLS.str("debugger.step_out"), ICON_STEP_OUT) {
			override fun actionPerformed(e: ActionEvent) {
				controller.stepOut()
			}
		}
		stepOut.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.step_out"))

		val stop = object : AbstractAction(NLS.str("debugger.stop"), ICON_STOP_GRAY) {
			override fun actionPerformed(e: ActionEvent) {
				controller.stop()
			}
		}
		stop.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.stop"))

		val run = object : AbstractAction(NLS.str("debugger.run"), ICON_RUN) {
			override fun actionPerformed(e: ActionEvent) {
				if (controller.isDebugging()) {
					if (controller.isSuspended()) {
						controller.run()
					} else {
						controller.pause()
					}
				}
			}
		}
		run.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.run"))

		val rerun = object : AbstractAction(NLS.str("debugger.rerun"), ICON_RERUN) {
			override fun actionPerformed(e: ActionEvent) {
				if (controller.isDebugging()) {
					controller.stop()
				}
				val pkgName = controller.getProcessName()
				if (pkgName.isEmpty() || !ADBDialog.launchForDebugging(mainWindow, pkgName, true)) {
					ADBDialog(mainWindow).isVisible = true
				}
			}
		}
		rerun.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.rerun"))

		controller.setStateListener(object : IDebugController.StateListener {
			var isGray = true

			override fun onStateChanged(suspended: Boolean, stopped: Boolean) {
				UiUtils.uiRun {
					if (!stopped) {
						if (isGray) {
							stop.putValue(Action.SMALL_ICON, ICON_STOP)
						}
					} else {
						stop.putValue(Action.SMALL_ICON, ICON_STOP_GRAY)
						run.putValue(Action.SMALL_ICON, ICON_RUN)
						run.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.run"))
						isGray = true
						return@uiRun
					}
					if (suspended) {
						run.putValue(Action.SMALL_ICON, ICON_RUN)
						run.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.run"))
					} else {
						run.putValue(Action.SMALL_ICON, ICON_PAUSE)
						run.putValue(Action.SHORT_DESCRIPTION, NLS.str("debugger.pause"))
					}
				}
			}
		})

		val toolBar = JToolBar()
		toolBar.add(Label())
		toolBar.add(Box.createHorizontalGlue())
		toolBar.add(rerun)
		toolBar.add(Box.createRigidArea(Dimension(5, 0)))
		toolBar.add(stop)
		toolBar.add(Box.createRigidArea(Dimension(5, 0)))
		toolBar.add(run)
		toolBar.add(Box.createRigidArea(Dimension(5, 0)))
		toolBar.add(stepOver)
		toolBar.add(Box.createRigidArea(Dimension(5, 0)))
		toolBar.add(stepInto)
		toolBar.add(Box.createRigidArea(Dimension(5, 0)))
		toolBar.add(stepOut)
		toolBar.add(Box.createHorizontalGlue())
		toolBar.add(Label())
		regShortcuts()
		return toolBar
	}

	private fun unregShortcuts() {
		KeyboardFocusManager
			.getCurrentKeyboardFocusManager()
			.removeKeyEventDispatcher(controllerShortCutDispatcher)
	}

	private fun regShortcuts() {
		controllerShortCutDispatcher = object : KeyEventDispatcher {
			override fun dispatchKeyEvent(e: KeyEvent): Boolean {
				if (e.getID() == KeyEvent.KEY_PRESSED &&
					mainWindow.getTabbedPane().focusedComp is SmaliArea
				) {
					if (e.getModifiersEx() == KeyEvent.SHIFT_DOWN_MASK &&
						e.getKeyCode() == KeyEvent.VK_F8
					) {
						controller.stepOut()
						return true
					}
					when (e.getKeyCode()) {
						KeyEvent.VK_F7 -> {
							controller.stepInto()
							return true
						}

						KeyEvent.VK_F8 -> {
							controller.stepOver()
							return true
						}

						KeyEvent.VK_F9 -> {
							controller.run()
							return true
						}
					}
				}
				return false
			}
		}
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
			.addKeyEventDispatcher(controllerShortCutDispatcher)
	}

	private fun treeNodeRightClicked(e: MouseEvent) {
		val path = variableTree.getPathForLocation(e.getX(), e.getY())
		if (path != null) {
			val node = path.getLastPathComponent()
			if (node is ValueTreeNode) {
				varTreeMenu.show(node, e.getComponent(), e.getX(), e.getY())
			}
		}
	}

	private fun stackFrameSelected(p: Point) {
		val loc = stackFrameList.locationToIndex(p)
		if (loc > -1) {
			val ele = stackFrameList.getModel().getElementAt(loc)
			if (ele != null) {
				ele.onSelected()
			}
		}
	}

	/** 启动调试会话并刷新界面。 */
	fun showDebugger(procName: String, host: String, port: Int, androidVer: Int, device: ADBDevice, pid: String): Boolean {
		val ok = controller.startDebugger(this, host, port, androidVer)
		if (ok) {
			UiUtils.uiRun {
				log(String.format("Attached %s %s:%d", procName, host, port))
				try {
					logcatPanel.init(device, pid)
				} catch (e: Exception) {
					log(NLS.str("logcat.error_fail_start"))
					LOG.error("Logcat failed to start", e)
				}
				leftSplitter.setDividerLocation(mainWindow.getSettings().debuggerStackFrameSplitterLoc)
				rightSplitter.setDividerLocation(mainWindow.getSettings().debuggerVarTreeSplitterLoc)
				mainWindow.showDebuggerPanel()
			}
		}
		return ok
	}

	val dbgController: IDebugController get() = controller

	val leftSplitterLocation: Int get() = leftSplitter.getDividerLocation()

	val rightSplitterLocation: Int get() = rightSplitter.getDividerLocation()

	fun loadSettings() {
		UiUtils.uiThreadGuard()

		val font: Font = mainWindow.getSettings().codeFont
		variableTree.setFont(font.deriveFont(font.getSize() + 1f))
		variableTree.setRowHeight(-1)
		stackFrameList.setFont(font)
		threadBox.setFont(font)
		logger.setFont(font)
	}

	fun resetUI() {
		UiUtils.uiThreadGuard()

		thisTreeNode.removeAllChildren()
		regTreeNode.removeAllChildren()

		clearFrameAndThreadList()

		threadBox.updateUI()
		stackFrameList.updateUI()
		variableTreeModel.reload(rootTreeNode)
		variableTree.expandPath(TreePath(rootTreeNode.getPath()))
		logger.setText("")
	}

	fun scrollToSmaliLine(cls: JClass, pos: Int, debugMode: Boolean) {
		SwingUtilities.invokeLater { getMainWindow().getTabsController().smaliJump(cls, pos, debugMode) }
	}

	fun resetAllDebuggingInfo() {
		UiUtils.uiThreadGuard()
		clearFrameAndThreadList()
		resetRegTreeNodes()
		resetThisTreeNodes()
	}

	fun resetThisTreeNodes() {
		thisTreeNode.removeAllChildren()
		SwingUtilities.invokeLater { variableTreeModel.reload(thisTreeNode) }
	}

	fun resetRegTreeNodes() {
		regTreeNode.removeAllChildren()
		SwingUtilities.invokeLater { variableTreeModel.reload(regTreeNode) }
	}

	fun updateRegTreeNodes(nodes: List<ValueTreeNode>) {
		nodes.forEach { regTreeNode.add(it) }
	}

	fun updateThisFieldNodes(nodes: List<ValueTreeNode>) {
		nodes.forEach { thisTreeNode.add(it) }
	}

	@Suppress("UNCHECKED_CAST")
	fun refreshThreadBox(elements: List<IListElement>) {
		UiUtils.uiRun {
			if (!elements.isEmpty()) {
				val model = threadBox.getModel() as DefaultComboBoxModel<IListElement>
				elements.forEach { model.addElement(it) }
			}
			threadBox.updateUI()
			stackFrameList.setFont(mainWindow.getSettings().codeFont)
		}
	}

	@Suppress("UNCHECKED_CAST")
	fun refreshStackFrameList(elements: List<IListElement>) {
		UiUtils.uiRun {
			if (!elements.isEmpty()) {
				val model = stackFrameList.getModel() as DefaultListModel<IListElement>
				model.addAll(elements)
				stackFrameList.setFont(mainWindow.getSettings().codeFont)
			}
			stackFrameList.repaint()
		}
	}

	fun refreshRegisterTree() {
		SwingUtilities.invokeLater {
			variableTreeModel.reload(regTreeNode)
			variableTree.expandPath(TreePath(regTreeNode.getPath()))
		}
	}

	fun refreshThisFieldTree() {
		SwingUtilities.invokeLater {
			val expanded = variableTree.isExpanded(TreePath(thisTreeNode.getPath()))
			variableTreeModel.reload(thisTreeNode)
			if (expanded) {
				variableTree.expandPath(TreePath(regTreeNode.getPath()))
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	fun clearFrameAndThreadList() {
		(stackFrameList.getModel() as DefaultListModel<IListElement>).removeAllElements()
		(threadBox.getModel() as DefaultComboBoxModel<IListElement>).removeAllElements()
	}

	fun log(msg: String) {
		val sb = StringBuilder()
		sb.append(" > ")
			.append(StringUtils.dateText)
			.append(" ")
			.append(msg)
			.append("\n")
		SwingUtilities.invokeLater {
			logger.append(sb.toString())
		}
	}

	fun updateRegTree(node: ValueTreeNode) {
		SwingUtilities.invokeLater {
			variableTreeModel.reload(regTreeNode)
			scrollToUpdatedNode(node)
		}
	}

	fun updateThisTree(node: ValueTreeNode) {
		SwingUtilities.invokeLater {
			variableTreeModel.reload(thisTreeNode)
			scrollToUpdatedNode(node)
		}
	}

	fun scrollToUpdatedNode(node: ValueTreeNode) {
		SwingUtilities.invokeLater {
			val path: Array<TreeNode> = node.getPath()
			variableTree.scrollPathToVisible(TreePath(path))
		}
	}

	/**
	 * 变量树节点基类。
	 *
	 * **做什么**：在 [DefaultMutableTreeNode] 基础上增加「值是否已更新」标记，
	 * 并定义名称/值/类型/类型 ID 以及更新方法，供 `DebugController` 的寄存器与字段节点实现。
	 *
	 * **为什么不用 `data class`**：这是树节点，必须保留基于身份的 `equals`/`hashCode`。
	 */
	abstract class ValueTreeNode : DefaultMutableTreeNode() {
		private var updatedFlag = false

		fun setUpdated(updated: Boolean) {
			this.updatedFlag = updated
		}

		val isUpdated: Boolean get() = updatedFlag

		abstract fun getName(): String

		abstract fun getValue(): String?

		abstract fun getType(): String?

		abstract fun getTypeID(): Long

		abstract fun updateValue(value: String?): ValueTreeNode

		abstract fun updateType(value: String?): ValueTreeNode

		abstract fun updateTypeID(id: Long): ValueTreeNode

		override fun toString(): String {
			val sb = StringBuilder(64)
			sb.append(getName())
			val value = getValue()
			if (value != null) {
				sb.append(" val: ").append(value).append(",")
			}
			val type = getType()
			if (type != null) {
				sb.append(" type: ").append(getType())
				val id = getTypeID()
				if (id > 0) {
					sb.append("@").append(id)
				}
			}
			if (value == null && type == null) {
				sb.append(" undefined")
			}
			return sb.toString()
		}
	}

	/** 线程/堆栈帧列表元素：被选中时的回调。 */
	interface IListElement {
		fun onSelected()
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(LogcatPanel::class.java)

		private val ICON_RUN: ImageIcon = UiUtils.openSvgIcon("debugger/execute")
		private val ICON_RERUN: ImageIcon = UiUtils.openSvgIcon("debugger/rerun")
		private val ICON_PAUSE: ImageIcon = UiUtils.openSvgIcon("debugger/threadFrozen")
		private val ICON_STOP: ImageIcon = UiUtils.openSvgIcon("debugger/suspend")
		private val ICON_STOP_GRAY: ImageIcon = UiUtils.openSvgIcon("debugger/suspendGray")
		private val ICON_STEP_INTO: ImageIcon = UiUtils.openSvgIcon("debugger/traceInto")
		private val ICON_STEP_OVER: ImageIcon = UiUtils.openSvgIcon("debugger/traceOver")
		private val ICON_STEP_OUT: ImageIcon = UiUtils.openSvgIcon("debugger/stepOut")
	}
}
