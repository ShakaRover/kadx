package jadx.gui.ui.graphs

import jadx.core.Consts
import jadx.core.clsp.ClspClass
import jadx.core.clsp.ClspGraph
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.MethodOverrideAttr
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.DotGraphUtils
import jadx.core.utils.Pair
import jadx.core.utils.StringUtils
import jadx.gui.treemodel.JClass
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.layout.WrapLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.ArrayList
import java.util.Formatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * 类继承图：同时展示父类（super）与子类（subclass）关系（PR #2935）。
 *
 * **做什么**：以当前类为中心，按 [distanceLimit] 步数双向遍历继承图；
 * 可选择是否显示同级（siblings）、长类名，以及方法覆写详情。
 *
 * **线程模型**：保持 Swing 模型，[reload] 用 `SwingUtilities.invokeLater` 更新图形。
 */
class ClassInheritanceGraphDialog(mainWindow: MainWindow, private val cls: ClassNode) :
	GraphDialog(
		mainWindow,
		"${NLS.str("graph_viewer.inheritance_graph.title")}: ${DotGraphUtils.classFormatName(cls, false)}",
	) {

	private var longNames = false
	private var overrides = false
	private var siblings = false
	private var distanceLimit = 3

	private lateinit var nodesToAdd: MutableSet<String>
	private lateinit var edgesToAdd: MutableSet<Pair<String>>

	private lateinit var nameToNodeID: MutableMap<String, Int>
	private var nextNodeID = 0

	override fun addMenuBar(): JMenuBar {
		val menuBar = super.addMenuBar()

		// 「显示长名称」复选框
		val showLongNames = JCheckBox(NLS.str("graph_viewer.long_names"))
		showLongNames.isSelected = false
		showLongNames.addItemListener {
			longNames = showLongNames.isSelected
			reload()
		}

		// 「显示覆写」复选框
		val showOverrides = JCheckBox(NLS.str("graph_viewer.overrides"))
		showOverrides.isSelected = false
		showOverrides.addItemListener {
			overrides = showOverrides.isSelected
			reload()
		}

		// 「显示同级」复选框
		val showSiblings = JCheckBox(NLS.str("graph_viewer.inheritance_graph.siblings"))
		showSiblings.isSelected = false
		showSiblings.addItemListener {
			siblings = showSiblings.isSelected
			reload()
		}

		// 距离微调框
		val distanceSpinnerModel = SpinnerNumberModel(3, 0, 100, 1)
		val distanceSpinner = JSpinner(distanceSpinnerModel)
		distanceSpinner.addChangeListener {
			distanceLimit = distanceSpinner.value as Int
			reload()
		}

		// 距离标签
		val distanceLbl = JLabel(NLS.str("graph_viewer.inheritance_graph.distance"))
		distanceLbl.labelFor = distanceSpinner
		distanceLbl.horizontalAlignment = SwingConstants.LEFT

		// 组装距离面板
		val distancePanel = JPanel()
		distancePanel.isOpaque = false
		distancePanel.layout = BoxLayout(distancePanel, BoxLayout.LINE_AXIS)
		distancePanel.add(distanceSpinner)
		distancePanel.add(Box.createRigidArea(Dimension(3, 0)))
		distancePanel.add(distanceLbl)

		// 组装菜单栏面板
		val menuBarPanel = JPanel()
		menuBarPanel.isOpaque = false
		menuBarPanel.layout = WrapLayout(FlowLayout.LEFT)
		menuBarPanel.add(showLongNames, BorderLayout.PAGE_START)
		menuBarPanel.add(showOverrides, BorderLayout.PAGE_START)
		menuBarPanel.add(showSiblings, BorderLayout.PAGE_START)
		menuBarPanel.add(distancePanel, BorderLayout.PAGE_START)

		menuBar.add(menuBarPanel)
		return menuBar
	}

	private fun reload() {
		SwingUtilities.invokeLater {
			val graph = generateGraph(cls)
			getPanel().setGraph(graph)
		}
	}

	private fun generateGraph(rootClass: ClassNode): String {
		// 重置状态
		this.nameToNodeID = HashMap()
		this.nextNodeID = 0
		this.nodesToAdd = HashSet()
		this.edgesToAdd = HashSet()

		val sb = StringBuilder()
		return Formatter(sb).use { f ->
			// 图头部
			addGraphHeader(f)

			val root: RootNode = rootClass.root()
			val rootClassName = rootClass.classInfo.type.getObject()
			val classGraph: ClspGraph = checkNotNull(root.getClsp())
			val inheritanceData = InheritanceDataAttr.get(root)

			// 收集 distanceLimit 步内的节点与边
			visitClass(inheritanceData, rootClassName, distanceLimit)

			// 加入节点与边
			addNodes(f, root, classGraph, rootClassName)
			addEdges(f)

			f.format("}")
			f.toString()
		}
	}

	/**
	 * 从 [rawName] 出发，在 [distanceLimit] 步内收集所有节点与边。
	 *
	 * [siblings] 为 false 时只显示直接层级（父类的父类、子类的子类）；
	 * 为 true 时显示整个图（子类的其它父类、父类的其它子类）。
	 */
	private fun visitClass(inheritanceData: InheritanceDataAttr, rawName: String, distanceLimit: Int) {
		visitClass(inheritanceData, rawName, distanceLimit, true, true)
	}

	private fun visitClass(
		inheritanceData: InheritanceDataAttr,
		rawName: String,
		distanceLimit: Int,
		visitChildren: Boolean,
		visitParents: Boolean,
	) {
		// 已访问过的类不再处理
		if (nodesToAdd.contains(rawName)) {
			return
		}
		nodesToAdd.add(rawName)

		// 到达距离上限则停止
		if (distanceLimit <= 0) {
			return
		}
		if (visitParents) {
			val parents = inheritanceData.getParents(rawName)
			for (parent in parents) {
				// 不显示 java.lang.Object
				if (parent == Consts.CLASS_OBJECT) {
					continue
				}
				val edge = Pair(parent, rawName)
				if (edgesToAdd.contains(edge)) {
					// 边已存在，两侧都已访问过，无需重复访问
					continue
				}
				edgesToAdd.add(edge)
				visitClass(inheritanceData, parent, distanceLimit - 1, siblings, true)
			}
		}
		if (visitChildren) {
			val children = inheritanceData.getChildren(rawName)
			for (child in children) {
				val edge = Pair(rawName, child)
				if (edgesToAdd.contains(edge)) {
					continue
				}
				edgesToAdd.add(edge)
				visitClass(inheritanceData, child, distanceLimit - 1, true, siblings)
			}
		}
	}

	/** 把节点与边的格式说明写入图头部。 */
	private fun addGraphHeader(f: Formatter) {
		val themeBackground = checkNotNull(UIManager.getColor("Panel.background"))
		val themeForeground = checkNotNull(UIManager.getColor("Label.foreground"))
		val themeShade = checkNotNull(UIManager.getColor("TextArea.background"))

		val bgColor = "bgcolor=" + DotGraphUtils.formatColor(themeBackground)
		val lineColor = "color=" + DotGraphUtils.formatColor(themeForeground)
		val fontColor = "fontcolor=" + DotGraphUtils.formatColor(themeForeground)
		val shadeColor = "fillcolor=" + DotGraphUtils.formatColor(themeShade)

		f.format("digraph G {\n")
		f.format("%s\n", bgColor)
		f.format("node[shape=\"record\" style=\"filled\" %s %s %s %s]\n", FONT, fontColor, lineColor, shadeColor)
		f.format("edge[arrowtail=\"onormal\" arrowhead=\"onormal\" %s %s %s]\n", FONT, fontColor, lineColor)
	}

	/** 把 [edgesToAdd] 中的所有边写入图。 */
	private fun addEdges(f: Formatter) {
		for (edge in edgesToAdd) {
			val firstID = nameToNodeID[edge.first] ?: continue
			val secondID = nameToNodeID[edge.second] ?: continue
			f.format("Node_%d -> Node_%d\n", firstID, secondID)
		}
	}

	/** 把 [nodesToAdd] 中的所有节点写入图。 */
	private fun addNodes(f: Formatter, root: RootNode, classGraph: ClspGraph, rootClassName: String) {
		val themeHighlight = checkNotNull(UIManager.getColor("Component.focusedBorderColor"))
		val themeOutOfFocus = checkNotNull(UIManager.getColor("Component.disabledBorderColor"))

		val highlightColor = "color=" + DotGraphUtils.formatColor(themeHighlight)
		val outOfFocus = "color=" + DotGraphUtils.formatColor(themeOutOfFocus)

		for (node in nodesToAdd) {
			val classNode = root.resolveClass(node)
			if (classNode == null) {
				// 无法解析完整 ClassNode，尝试解析部分 ClspClass 信息
				val clspClass = classGraph.getClsDetails(node)
				if (clspClass == null) {
					addNode(f, node, outOfFocus)
				} else {
					addNode(f, clspClass, outOfFocus)
				}
			} else {
				// 高亮根类
				val extra = if (node == rootClassName) highlightColor else ""
				addNode(f, classNode, extra)
			}
		}
	}

	/**
	 * 为 [ClassNode] 加入图节点（接口用虚线填充样式），返回节点 id。
	 */
	private fun addNode(f: Formatter, cls: ClassNode, extraIn: String): Int {
		val rawName = cls.classInfo.type.getObject()
		nameToNodeID[rawName]?.let { return it }

		val nodeID = nextNodeID++
		nameToNodeID[rawName] = nodeID

		// 接口用虚线填充
		var extra = extraIn
		if (cls.accessFlags.isInterface()) {
			extra += " style=\"dashed, filled\""
		}

		val name = DotGraphUtils.classFormatName(cls, longNames)
		f.format("Node_%d [ label=\"{%s\\ ", nodeID, DotGraphUtils.toDotNodeName(name))

		if (overrides) {
			f.format("|")
			val table: MutableList<Pair<String>> = ArrayList()
			for (method in cls.methods) {
				val ovrdAttr: MethodOverrideAttr? = method.get(AType.METHOD_OVERRIDE)
				if (ovrdAttr != null && ovrdAttr.overrideList.isNotEmpty()) {
					val methodName = DotGraphUtils.methodFormatName(method, longNames)
					val details = Formatter()
					details.format(" overrides ")
					for (baseMthDetails in ovrdAttr.overrideList) {
						val baseClassName = DotGraphUtils.classFormatName(baseMthDetails.methodInfo.declClass, longNames)
						details.format("%s, ", baseClassName)
					}
					val detailsString = StringUtils.removeSuffix(details.toString(), ", ")
					table.add(Pair(methodName, detailsString))
					details.close()
				}
			}
			if (table.isNotEmpty()) {
				val longestLength = table.maxOf { it.first.length }
				for (entry in table) {
					f.format("%-" + longestLength + "s %s\\l", entry.first, entry.second)
				}
			} else {
				f.format("No overrides.")
			}
		}

		f.format("}\" %s]\n", extra)
		return nodeID
	}

	/**
	 * 为可解析为 [ClspClass] 但无法解析为 [ClassNode] 的名字加入图节点（置灰）。
	 */
	private fun addNode(f: Formatter, cls: ClspClass, extraIn: String): Int {
		val rawName = cls.name
		nameToNodeID[rawName]?.let { return it }

		val nodeID = nextNodeID++
		nameToNodeID[rawName] = nodeID

		// 接口用虚线填充
		var extra = extraIn
		if (cls.isInterface()) {
			extra += " style=\"dashed, filled\""
		}

		val name = DotGraphUtils.rawNameFormatName(rawName, this.cls.root(), longNames)
		f.format("Node_%d [ label=\"{%s\\ ", nodeID, DotGraphUtils.toDotNodeName(name))
		f.format("}\" %s]\n", extra)
		return nodeID
	}

	/**
	 * 为无法解析为 [ClassNode]/[ClspClass] 的原始类名加入图节点（置灰）。
	 */
	private fun addNode(f: Formatter, rawName: String, extra: String): Int {
		nameToNodeID[rawName]?.let { return it }

		val nodeID = nextNodeID++
		nameToNodeID[rawName] = nodeID

		val name = DotGraphUtils.rawNameFormatName(rawName, cls.root(), longNames)
		f.format("Node_%d [ label=\"{%s}\" %s]\n", nodeID, DotGraphUtils.toDotNodeName(name), extra)
		return nodeID
	}

	companion object {
		private const val serialVersionUID = 938883901412562913L

		private const val FONT = "fontname=\"Courier\" fontsize=12"

		fun open(window: MainWindow, node: JClass) {
			val cls = node.getCls().getClassNode()
			val graphDialog = ClassInheritanceGraphDialog(window, cls)
			graphDialog.addMenuBar()
			graphDialog.isVisible = true
			graphDialog.reload()
		}
	}
}
