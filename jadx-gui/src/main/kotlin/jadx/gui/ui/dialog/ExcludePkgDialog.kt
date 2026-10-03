package jadx.gui.ui.dialog

import jadx.api.JavaPackage
import jadx.gui.ui.MainWindow
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.HashMap
import java.util.HashSet
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.WindowConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * 「排除包」对话框：以复选框树选择要排除的包，确认后重开工程。
 *
 * **做什么**：把工程的包列表构建成层级树，支持全选/反选/取消选择，
 * 点击「确定」写回排除列表并重开。
 *
 * **为什么保留 Swing 线程模型**：全部操作在 EDT 上，不引入协程。
 */
class ExcludePkgDialog(private val mainWindow: MainWindow) : JDialog(mainWindow) {

	private lateinit var tree: JTree
	private lateinit var treeRoot: DefaultMutableTreeNode
	private val roots: MutableList<PkgNode> = ArrayList()

	init {
		initUI()
		UiUtils.addEscapeShortCutToDispose(this)
		initPackageList()
	}

	private fun initUI() {
		title = NLS.str("exclude_dialog.title")
		tree = JTree()
		tree.setRowHeight(-1)
		treeRoot = DefaultMutableTreeNode("Packages")
		val treeModel = DefaultTreeModel(treeRoot)
		tree.setModel(treeModel)
		tree.setCellRenderer(PkgListCellRenderer())
		val listPanel = JScrollPane(tree)
		listPanel.setBorder(BorderFactory.createEmptyBorder())
		tree.addMouseListener(object : MouseAdapter() {
			override fun mousePressed(e: MouseEvent) {
				val path = tree.getPathForLocation(e.getX(), e.getY())
				if (path != null && path.getLastPathComponent() is PkgNode) {
					val node = path.getLastPathComponent() as PkgNode
					node.toggle()
					repaint()
				}
			}
		})

		val actionPanel = JPanel()
		actionPanel.setLayout(BoxLayout(actionPanel, BoxLayout.LINE_AXIS))
		val btnOk = JButton(NLS.str("common_dialog.ok"))
		val btnAll = JButton(NLS.str("exclude_dialog.select_all"))
		val btnInvert = JButton(NLS.str("exclude_dialog.invert"))
		val btnDeselect = JButton(NLS.str("exclude_dialog.deselect"))
		actionPanel.add(btnDeselect)
		actionPanel.add(Box.createRigidArea(Dimension(10, 0)))
		actionPanel.add(btnInvert)
		actionPanel.add(Box.createRigidArea(Dimension(10, 0)))
		actionPanel.add(btnAll)
		actionPanel.add(Box.createHorizontalGlue())
		actionPanel.add(btnOk, BorderLayout.PAGE_END)

		val mainPane = JPanel(BorderLayout(5, 5))
		mainPane.add(listPanel, BorderLayout.CENTER)
		mainPane.add(actionPanel, BorderLayout.SOUTH)
		mainPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

		getContentPane().add(mainPane)
		pack()
		setSize(600, 700)
		setLocationRelativeTo(null)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
		setModalityType(ModalityType.MODELESS)

		btnOk.addActionListener {
			mainWindow.getWrapper().setExcludedPackages(getExcludes())
			mainWindow.reopen()
			dispose()
		}
		btnAll.addActionListener {
			roots.forEach { p -> p.setSelected(true) }
			tree.updateUI()
		}
		btnDeselect.addActionListener {
			roots.forEach { p -> p.setSelected(false) }
			tree.updateUI()
		}
		btnInvert.addActionListener {
			roots.forEach { p -> p.toggle() }
			tree.updateUI()
		}
	}
	private fun initPackageList() {
		val pkgs = mainWindow.getWrapper().getPackages()
			.stream()
			.map { obj -> obj.getFullName() }
			.collect(Collectors.toList())
		getPackageTree(pkgs).forEach { pkg -> treeRoot.add(pkg) }
		initCheckbox()
		tree.expandPath(TreePath(treeRoot.getPath()))
	}

	private fun getPackageTree(names: List<String>): List<PkgNode> {
		val pkgRoots = ArrayList<PkgNode>()
		val nameSet = HashSet<String>()
		val childMap = HashMap<String, MutableList<PkgNode>>()
		for (name in names) {
			var parent = ""
			var last = 0
			do {
				var pos = name.indexOf('.', last)
				if (pos == -1) {
					pos = name.length
				}
				val fullName = name.substring(0, pos)
				if (!nameSet.contains(fullName)) {
					nameSet.add(fullName)
					val node = PkgNode(fullName, name.substring(last, pos))
					if (parent.isNotEmpty()) {
						childMap.computeIfAbsent(parent) { ArrayList() }.add(node)
					} else {
						pkgRoots.add(node)
					}
				}
				parent = fullName
				last = pos + 1
			} while (last < name.length)
		}
		addToParent(null, pkgRoots, childMap)
		return this.roots
	}

	private fun addToParent(parent: PkgNode?, roots: List<PkgNode>, childMap: Map<String, List<PkgNode>>): PkgNode? {
		for (initialRoot in roots) {
			var root = initialRoot
			var tempFullName = root.fullName
			while (true) {
				val children = childMap[tempFullName]
				if (children != null) {
					if (children.size == 1) {
						val next = children[0]
						next.name = root.name + "." + next.name
						tempFullName = next.fullName
						next.fullName = root.fullName
						root = next
						continue
					} else {
						addToParent(root, children, childMap)
					}
				}
				if (parent == null) {
					this.roots.add(root)
				} else {
					parent.add(root)
				}
				break
			}
		}
		return parent
	}

	private fun getExcludes(): List<String> {
		val excludes = ArrayList<String>()
		walkTree(true) { p -> excludes.add(p.fullName) }
		return excludes
	}

	private fun initCheckbox() {
		val tmp = mainWindow.getSettings().getCodeFont()
		val font = tmp.deriveFont(tmp.getSize() + 1.0f)
		val excluded = HashSet(mainWindow.getWrapper().getExcludedPackages())
		walkTree(false) { p -> p.initCheckbox(excluded.contains(p.fullName), font) }
	}

	private fun walkTree(findSelected: Boolean, consumer: Consumer<PkgNode>) {
		val queue = ArrayList(roots)
		var i = 0
		while (i < queue.size) {
			val node = queue[i]
			if (findSelected && node.isSelected()) {
				consumer.accept(node)
			} else {
				if (!findSelected) {
					consumer.accept(node)
				}
				for (j in 0 until node.getChildCount()) {
					queue.add(node.getChildAt(j) as PkgNode)
				}
			}
			i++
		}
	}

	private class PkgNode(fullName: String, name: String) : DefaultMutableTreeNode() {
		var name: String = name
		var fullName: String = fullName
		lateinit var checkbox: JCheckBox

		fun initCheckbox(select: Boolean, font: Font) {
			var selected = select
			if (!selected) {
				if (getParent() is PkgNode) {
					selected = (getParent() as PkgNode).isSelected()
				}
			}
			checkbox = JCheckBox(name, selected)
			checkbox.setFont(font)
		}

		fun toggle(): Boolean {
			val selected = !checkbox.isSelected
			setSelected(selected)
			toggleParents(selected)
			return selected
		}

		fun toggleParents(select: Boolean) {
			if (getParent() is PkgNode) {
				val p = getParent() as PkgNode
				if (select) {
					val allSelected = p.isChildrenAllSelected()
					if (allSelected) {
						p.checkbox.isSelected = true
						p.toggleParents(true)
					}
				} else {
					p.checkbox.isSelected = false
					p.toggleParents(false)
				}
			}
		}

		fun setSelected(select: Boolean) {
			checkbox.isSelected = select
			for (i in 0 until getChildCount()) {
				(getChildAt(i) as PkgNode).setSelected(select)
			}
		}

		fun isSelected(): Boolean = checkbox.isSelected

		fun getDisplayName(): String = name

		fun isChildrenAllSelected(): Boolean {
			for (i in 0 until getChildCount()) {
				if (!(getChildAt(i) as PkgNode).isSelected()) {
					return false
				}
			}
			return true
		}

		override fun toString(): String = name

		companion object {
			private const val serialVersionUID = -1111111202104151430L
		}
	}

	private class PkgListCellRenderer : DefaultTreeCellRenderer() {
		override fun getTreeCellRendererComponent(
			tree: JTree,
			value: Any?,
			selected: Boolean,
			expanded: Boolean,
			leaf: Boolean,
			row: Int,
			hasFocus: Boolean,
		): Component {
			if (value is PkgNode) {
				value.checkbox.setBackground(tree.getBackground())
				value.checkbox.setForeground(tree.getForeground())
				return value.checkbox
			}
			val c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
			setIcon(Icons.PACKAGE)
			return c
		}

		companion object {
			private const val serialVersionUID = -1111111202104151235L
		}
	}

	companion object {
		private const val serialVersionUID = -1111111202104151030L
	}
}
