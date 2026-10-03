package jadx.gui.ui.dialog

import jadx.api.ICodeInfo
import jadx.api.JavaClass
import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.api.utils.CodeUtils
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.visitors.prepare.CollectConstValues
import jadx.gui.JadxWrapper
import jadx.gui.jobs.TaskStatus
import jadx.gui.treemodel.CodeNode
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JField
import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.cellrenders.PathHighlightTreeCellRenderer
import jadx.gui.ui.panel.ProgressPanel
import jadx.gui.ui.panel.SimpleCodePanel
import jadx.gui.utils.ILoadListener
import jadx.gui.utils.JNodeCache
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Collections
import java.util.Enumeration
import java.util.HashMap
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * 「查找用法（增强版）」对话框：以可展开的树展示引用链，右侧显示代码。
 *
 * **做什么**：根节点为初始节点，展开节点时按需异步收集其引用并插入子节点；
 * 双击展开/折叠，右键菜单可跳转或复制引用路径。
 *
 * **为什么保留 Swing 线程模型**：后台收集走 [MainWindow.getBackgroundExecutor]（协程调度），
 * 树更新回到 EDT。
 */
class UsageDialogPlus private constructor(
	mainWindow: MainWindow,
	private val initialNode: JNode,
) : CommonSearchDialog(mainWindow, NLS.str("usage_dialog_plus.title")) {

	private val mainPanel: JPanel
	private val splitPane: JSplitPane
	private val simpleCodePanel: SimpleCodePanel
	private val usageTree: JTree
	private val treeModel: DefaultTreeModel
	private val rootNode: DefaultMutableTreeNode
	private val localProgressPanel: ProgressPanel

	// 与父类同名的字段在 Kotlin 中不能遮蔽，这里改名
	private val plusResultsInfoLabel: JLabel
	private val plusProgressInfoLabel: JLabel

	init {
		// 初始化进度面板与警告标签
		progressPane = ProgressPanel(mainWindow, false)
		warnLabel = JLabel()
		warnLabel.setForeground(Color.RED)
		warnLabel.setVisible(false)

		// 初始化结果/进度信息标签
		plusResultsInfoLabel = JLabel()
		plusProgressInfoLabel = JLabel()
		localProgressPanel = ProgressPanel(mainWindow, false)

		mainPanel = JPanel()
		mainPanel.setLayout(BorderLayout())

		// 代码面板
		simpleCodePanel = SimpleCodePanel(mainWindow)

		// 树
		rootNode = DefaultMutableTreeNode(initialNode)
		treeModel = DefaultTreeModel(rootNode)
		usageTree = JTree(treeModel)
		usageTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)
		usageTree.setRootVisible(true)
		usageTree.setShowsRootHandles(true)
		usageTree.putClientProperty("JTree.lineStyle", "Horizontal")
		usageTree.setRowHeight(22)
		usageTree.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5))
		usageTree.setFont(mainWindow.getSettings().codeFont)

		// 使用自定义渲染器代替自定义 UI
		usageTree.setCellRenderer(PathHighlightTreeCellRenderer())

		// 树选择监听：展示代码并刷新结果信息
		usageTree.addTreeSelectionListener {
			val node1 = usageTree.getLastSelectedPathComponent() as? DefaultMutableTreeNode
			if (node1 != null) {
				val nodeInfo = node1.getUserObject()
				if (nodeInfo is CodeNode) {
					simpleCodePanel.showCode(nodeInfo, nodeInfo.makeDescString())
				} else if (nodeInfo is JNode) {
					simpleCodePanel.showCode(nodeInfo, nodeInfo.makeDescString() ?: "")
				}
				// 更新结果信息：显示当前选中节点的子节点数
				updateResultsInfo(node1)
			}
		}

		usageTree.addTreeExpansionListener(object : TreeExpansionListener {
			override fun treeExpanded(event: TreeExpansionEvent) {
				val path = event.getPath()
				val expandedNode = path.getLastPathComponent() as DefaultMutableTreeNode

				// 仅在节点没有子节点时加载
				if (expandedNode.getChildCount() == 0) {
					val userObject = expandedNode.getUserObject()
					if (userObject is JNode) {
						var nodeToUse: JNode = userObject
						// 若为 CodeNode，先转换成实际 JNode 再搜索其引用
						if (nodeToUse.javaClass === CodeNode::class.java) {
							nodeToUse = getNodeFromCodeNode(nodeToUse as CodeNode) ?: return
						}
						loadNodeUsages(nodeToUse, expandedNode)
					}
				}
			}

			override fun treeCollapsed(event: TreeExpansionEvent) {
				// 无需处理
			}
		})

		usageTree.addMouseListener(object : MouseAdapter() {
			private var lastClickTime = 0L
			private var lastClickPath: TreePath? = null

			override fun mouseClicked(e: MouseEvent) {
				val path = usageTree.getPathForLocation(e.getX(), e.getY()) ?: return

				// 设置选中路径
				usageTree.setSelectionPath(path)

				// 右键菜单
				if (SwingUtilities.isRightMouseButton(e)) {
					val selectedNode = path.getLastPathComponent() as DefaultMutableTreeNode
					val userObject = selectedNode.getUserObject()
					if (userObject is JNode || userObject is CodeNode) {
						val nodeForMenu = if (userObject is JNode) userObject else getNodeFromCodeNode(userObject as CodeNode)
						showPopupMenu(e, nodeForMenu, path)
					}
					return
				}

				// 左键单击/双击
				if (SwingUtilities.isLeftMouseButton(e)) {
					val clickTime = System.currentTimeMillis()
					// 双击间隔阈值
					val doubleClickInterval = 300L
					val selectedNode = path.getLastPathComponent() as DefaultMutableTreeNode

					if ((clickTime - lastClickTime) < doubleClickInterval && path == lastClickPath) {
						// 双击：切换展开/折叠
						if (usageTree.isExpanded(path)) {
							usageTree.collapsePath(path)
						} else {
							usageTree.expandPath(path)
							loadUsagesIfNeeded(selectedNode)
						}
						updateResultsInfo(selectedNode)
					} else {
						// 单击：若未展开则展开
						if (!usageTree.isExpanded(path)) {
							usageTree.expandPath(path)
							loadUsagesIfNeeded(selectedNode)
						}
						updateResultsInfo(selectedNode)
					}

					lastClickTime = clickTime
					lastClickPath = path
				}
			}
		})

		// 分割面板
		splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
		splitPane.setOneTouchExpandable(true)
		splitPane.setContinuousLayout(true)
		splitPane.setResizeWeight(0.3) // 左侧 30%
		splitPane.setDividerSize(10) // 加宽分隔条方便拖拽

		val treeScrollPane = JScrollPane(usageTree)
		treeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
		treeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED)

		// 状态面板
		val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT))
		statusPanel.add(plusResultsInfoLabel)
		statusPanel.add(Box.createRigidArea(Dimension(10, 0)))
		statusPanel.add(plusProgressInfoLabel)
		statusPanel.add(Box.createRigidArea(Dimension(10, 0)))
		statusPanel.add(localProgressPanel)

		val leftPanel = JPanel(BorderLayout())
		leftPanel.add(treeScrollPane, BorderLayout.CENTER)
		leftPanel.add(statusPanel, BorderLayout.SOUTH)

		splitPane.setLeftComponent(leftPanel)
		splitPane.setRightComponent(simpleCodePanel)

		mainPanel.add(splitPane, BorderLayout.CENTER)

		initUI()
		registerInitOnOpen()
		loadWindowPos()
	}

	private fun initUI() {
		initCommon()
		val buttonPane = initButtonsPanel()

		val contentPanel = JPanel()
		contentPanel.setLayout(BorderLayout(5, 5))
		contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		contentPanel.add(mainPanel, BorderLayout.CENTER)
		contentPanel.add(buttonPane, BorderLayout.PAGE_END)
		getContentPane().add(contentPanel)

		pack()
		setSize(1300, 700)
		setLocationRelativeTo(null)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

		// 监听窗口尺寸变化，保证分割条位置适配
		addComponentListener(object : ComponentAdapter() {
			override fun componentResized(e: ComponentEvent) {
				val width = splitPane.getWidth()
				if (width > 0) {
					val currentDividerLocation = splitPane.getDividerLocation()
					val ratio = currentDividerLocation.toDouble() / width
					if (ratio < 0.2 || ratio > 0.5) {
						splitPane.setDividerLocation((width * 0.3).toInt())
					}
				}
			}
		})
	}

	/** 若选中节点尚无子节点，则按需加载其引用。 */
	private fun loadUsagesIfNeeded(selectedNode: DefaultMutableTreeNode) {
		if (selectedNode.getChildCount() == 0 && selectedNode.getUserObject() is JNode) {
			var nodeToUse: JNode = selectedNode.getUserObject() as JNode
			// 若为 CodeNode，先转换成实际 JNode 再搜索其引用
			if (nodeToUse.javaClass === CodeNode::class.java) {
				nodeToUse = getNodeFromCodeNode(nodeToUse as CodeNode) ?: return
			}
			loadNodeUsages(nodeToUse, selectedNode)
		}
	}
	override fun openInit() {
		prepareUsageData(initialNode)

		localProgressPanel.setIndeterminate(true)
		localProgressPanel.setVisible(true)
		plusProgressInfoLabel.setText(NLS.str("search_dialog.tip_searching"))

		// 加载根节点的引用
		loadNodeUsages(initialNode, rootNode)
	}

	private fun loadNodeUsages(node: JNode, treeNode: DefaultMutableTreeNode) {
		// 加载开始时显示搜索状态
		localProgressPanel.setIndeterminate(true)
		localProgressPanel.setVisible(true)
		plusProgressInfoLabel.setText(NLS.str("search_dialog.tip_searching"))

		mainWindow.getBackgroundExecutor().execute(
			NLS.str("progress.load"),
			Runnable { collectUsageData(node, treeNode) },
			{ status ->
				if (status == TaskStatus.CANCEL_BY_MEMORY) {
					mainWindow.showHeapUsageBar()
					UiUtils.errorMessage(this, NLS.str("message.memoryLow"))
				}
				localProgressPanel.setVisible(false)
				plusProgressInfoLabel.setText(NLS.str("usage_dialog_plus.search_complete"))
				// 更新结果信息：始终显示当前选中节点的子节点数
				updateResultsInfo(treeNode)

				// 展开根节点
				if (treeNode === rootNode) {
					usageTree.expandPath(TreePath(rootNode.getPath()))
				}
			},
		)
	}

	private fun updateResultsInfo(node: DefaultMutableTreeNode?) {
		if (node != null) {
			val childCount = node.getChildCount()
			plusResultsInfoLabel.setText(NLS.str("search_dialog.results_complete", childCount))
		}
	}

	private fun getTotalChildCount(node: DefaultMutableTreeNode): Int {
		var count = node.getChildCount()
		val e: Enumeration<TreeNode> = node.children()
		while (e.hasMoreElements()) {
			val child = e.nextElement() as DefaultMutableTreeNode
			count += getTotalChildCount(child)
		}
		return count
	}
	private fun prepareUsageData(node: JNode) {
		if (mainWindow.getSettings().isReplaceConsts && node is JField) {
			val fld: FieldNode = node.javaField.getFieldNode()
			val constField = CollectConstValues.getFieldConstValue(fld) != null
			if (constField && !fld.accessFlags.isPrivate()) {
				// 执行完整反编译，为全量代码扫描做准备
				mainWindow.requestFullDecompilation()
			}
		}
	}

	private fun collectUsageData(node: JNode, treeNode: DefaultMutableTreeNode) {
		val usageList = ArrayList<CodeNode>()
		buildUsageQuery(node).forEach { (searchNode, useNodes) ->
			useNodes.map { it.getTopParentClass() }
				.distinct()
				.forEach { u -> processUsage(searchNode, checkNotNull(u), usageList) }
		}

		// 排序后加入树节点
		Collections.sort(usageList)
		SwingUtilities.invokeLater {
			for (codeNode in usageList) {
				val usageTreeNode = DefaultMutableTreeNode(codeNode)
				treeModel.insertNodeInto(usageTreeNode, treeNode, treeNode.getChildCount())
			}
			treeModel.nodeStructureChanged(treeNode)
		}
	}

	private fun buildUsageQuery(node: JNode): Map<JavaNode, List<JavaNode>> {
		val map = HashMap<JavaNode, List<JavaNode>>()
		if (node is JMethod) {
			val javaMethod: JavaMethod = node.javaMethod
			for (mth in getMethodWithOverrides(javaMethod)) {
				map[mth] = mth.getUseIn()
			}
			return map
		}
		if (node is JClass) {
			val javaCls: JavaClass = node.getCls()
			map[javaCls] = javaCls.getUseIn()
			// 把构造函数的引用并入类的引用
			for (javaMth in javaCls.getMethods()) {
				if (javaMth.isConstructor()) {
					map[javaMth] = javaMth.getUseIn()
				}
			}
			return map
		}
		if (node is JField && mainWindow.getSettings().isReplaceConsts) {
			val fld: FieldNode = node.javaField.getFieldNode()
			val constField = CollectConstValues.getFieldConstValue(fld) != null
			if (constField && !fld.accessFlags.isPrivate()) {
				// 搜索全部类以收集被替换常量的引用
				map[checkNotNull(fld.javaNode)] = mainWindow.getWrapper().includedClasses
				return map
			}
		}
		val javaNode: JavaNode = checkNotNull(node.getJavaNode())
		map[javaNode] = javaNode.getUseIn()
		return map
	}

	private fun getMethodWithOverrides(javaMethod: JavaMethod): List<JavaMethod> {
		val relatedMethods = javaMethod.getOverrideRelatedMethods()
		if (relatedMethods.isNotEmpty()) {
			return relatedMethods
		}
		return Collections.singletonList(javaMethod)
	}

	private fun processUsage(searchNode: JavaNode, topUseClass: JavaClass, usageList: MutableList<CodeNode>) {
		val codeInfo: ICodeInfo = topUseClass.getCodeInfo()
		val usePositions = topUseClass.getUsePlacesFor(codeInfo, searchNode)
		if (usePositions.isEmpty()) {
			return
		}
		val code = codeInfo.getCodeStr()
		val wrapper: JadxWrapper = mainWindow.getWrapper()
		for (pos in usePositions) {
			val line = CodeUtils.getLineForPos(code, pos)
			if (line.startsWith("import ")) {
				continue
			}
			val nodeCache: JNodeCache = nodeCache
			val enclosingNode = wrapper.getEnclosingNode(codeInfo, pos)
			val rootJCls: JClass = checkNotNull(nodeCache.makeFrom(topUseClass))
			val usageJNode: JNode = if (enclosingNode == null) rootJCls else checkNotNull(nodeCache.makeFrom(enclosingNode))

			// 创建 CodeNode 并加入列表
			val codeNode = CodeNode(rootJCls, usageJNode, line.trim(), pos)
			usageList.add(codeNode)
		}
	}
	private fun getNodeFromCodeNode(codeNode: CodeNode?): JNode? {
		if (codeNode != null) {
			try {
				// 尝试获取 CodeNode 引用的实际节点
				val javaNode: JavaNode? = codeNode.getJavaNode()
				val nodeCache: JNodeCache = nodeCache
				var node: JNode? = nodeCache.makeFrom(javaNode)

				// 若无法直接获取，则回退到 jParent
				if (node == null) {
					node = codeNode.getJParent()
				}

				if (node != null) {
					LOG.debug("Converted CodeNode to {} of type {}", node.getName(), node.javaClass.getSimpleName())
				} else {
					LOG.debug("Failed to convert CodeNode: {}", codeNode.getName())
				}

				return node
			} catch (e: Exception) {
				LOG.error("Error converting CodeNode to JNode", e)
			}
		}
		return null
	}

	private fun showPopupMenu(e: MouseEvent, node: JNode?, path: TreePath) {
		if (node == null) {
			return
		}

		val popup = JPopupMenu()

		// 展开/加载引用菜单项
		val treeNode = path.getLastPathComponent() as DefaultMutableTreeNode
		val expandItem = JMenuItem(NLS.str("usage_dialog_plus.expand_usages"))
		expandItem.addActionListener {
			// 展开节点并加载引用
			if (treeNode.getChildCount() == 0) {
				var nodeToUse: JNode? = node
				// 若为 CodeNode，先转换成实际 JNode 再搜索其引用
				if (node.javaClass === CodeNode::class.java) {
					nodeToUse = getNodeFromCodeNode(node as CodeNode)
				}
				if (nodeToUse != null) {
					loadNodeUsages(nodeToUse, treeNode)
				}
			}
			usageTree.expandPath(path)
		}

		val jumpToItem = JMenuItem(NLS.str("usage_dialog_plus.jump_to"))
		jumpToItem.addActionListener { openItem(node) }

		val copyPathItem = JMenuItem(NLS.str("usage_dialog_plus.copy_path"))
		copyPathItem.addActionListener { copyUsagePath(path) }

		popup.add(expandItem)
		popup.addSeparator()
		popup.add(jumpToItem)
		popup.add(copyPathItem)
		popup.show(e.getComponent(), e.getX(), e.getY())
	}

	private fun copyUsagePath(path: TreePath?) {
		if (path != null) {
			val pathBuilder = StringBuilder()
			val nodes = path.getPath()

			// 从叶节点（当前选中节点）向根节点反向输出
			for (i in nodes.indices.reversed()) {
				val treeNode = nodes[i] as DefaultMutableTreeNode
				val userObject = treeNode.getUserObject()

				if (i < nodes.size - 1) {
					pathBuilder.append("\n")
					// 按反向计算的层级添加缩进
					val indentLevel = nodes.size - 1 - i
					for (j in 0 until indentLevel) {
						pathBuilder.append(" ")
					}
					pathBuilder.append("-> ")
				}

				if (userObject is JNode) {
					pathBuilder.append(checkNotNull(userObject.getJavaNode()).getCodeNodeRef().toString())
				} else if (userObject is CodeNode) {
					pathBuilder.append(checkNotNull(userObject.getJavaNode()).getCodeNodeRef().toString())
				}
			}

			UiUtils.copyToClipboard(pathBuilder.toString())
		}
	}
	override fun initButtonsPanel(): JPanel {
		progressPane = ProgressPanel(mainWindow, false)

		val cancelButton = JButton(NLS.str("search_dialog.cancel"))
		cancelButton.addActionListener { dispose() }
		val openBtn = JButton(NLS.str("search_dialog.open"))
		openBtn.addActionListener { openSelectedItem() }
		rootPane.defaultButton = openBtn

		val cbKeepOpen = JCheckBox(NLS.str("search_dialog.keep_open"))
		cbKeepOpen.isSelected = mainWindow.getSettings().isKeepCommonDialogOpen
		cbKeepOpen.addActionListener { mainWindow.getSettings().saveKeepCommonDialogOpen(cbKeepOpen.isSelected) }
		cbKeepOpen.setAlignmentY(Component.CENTER_ALIGNMENT)

		val buttonPane = JPanel()
		buttonPane.setLayout(BoxLayout(buttonPane, BoxLayout.LINE_AXIS))
		buttonPane.add(cbKeepOpen)
		buttonPane.add(Box.createRigidArea(Dimension(15, 0)))
		buttonPane.add(progressPane)
		buttonPane.add(Box.createRigidArea(Dimension(5, 0)))
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(openBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelButton)
		return buttonPane
	}

	override fun openSelectedItem() {
		// 获取当前选中节点
		val node = plusSelectedNode
		if (node == null) {
			return
		}
		openItem(node)
	}

	override fun loadFinished() {
		// 树加载已单独处理
	}

	override fun loadStart() {
		// 树加载已单独处理
	}

	private val plusSelectedNode: JNode? get() {
		try {
			val node = usageTree.getLastSelectedPathComponent() as? DefaultMutableTreeNode ?: return null

			val userObject = node.getUserObject()
			if (userObject is JNode) {
				return userObject
			} else if (userObject is CodeNode) {
				return getNodeFromCodeNode(userObject)
			}
			return null
		} catch (e: Exception) {
			LOG.error("Failed to get selected node", e)
			return null
		}
	}
	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(UsageDialogPlus::class.java)
		private const val serialVersionUID = -5105405789969134107L

		/** 打开增强版用法对话框。 */
		fun open(mainWindow: MainWindow, node: JNode) {
			val usageDialog = UsageDialogPlus(mainWindow, node)
			mainWindow.addLoadListener(object : ILoadListener {
				override fun update(loaded: Boolean): Boolean {
					if (!loaded) {
						usageDialog.dispose()
						return true
					}
					return false
				}
			})
			usageDialog.isVisible = true

			// 窗口显示后设置分割面板位置
			SwingUtilities.invokeLater {
				val width = usageDialog.splitPane.getWidth()
				if (width > 0) {
					usageDialog.splitPane.setDividerLocation((width * 0.3).toInt())
				}
			}
		}
	}
}
