package jadx.gui.ui.graphs

import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.DotGraphUtils
import jadx.gui.treemodel.JClass
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.layout.WrapLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.Formatter
import javax.swing.JCheckBox
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * 类方法图：以类中的方法为节点，展示方法之间的调用（caller）关系。
 *
 * **做什么**：从类的每个方法出发递归查找 caller（最多 [CALLER_DEPTH_LIMIT] 层），
 * 生成 GraphViz DOT 字符串交给 [GraphPanel] 渲染；可切换是否显示长方法名。
 *
 * **线程模型**：保持原 Swing 模型，[reload] 用 `SwingUtilities.invokeLater` 更新图形。
 */
class ClassMethodGraphDialog(mainWindow: MainWindow, private val cls: ClassNode) :
	GraphDialog(
		mainWindow,
		"${NLS.str("graph_viewer.method_graph.title")}: ${DotGraphUtils.classFormatName(cls, false)}",
	) {

	private var nextNodeID = 0
	private lateinit var methodToNodeID: MutableMap<JavaMethod, Int>
	private lateinit var edges: MutableSet<Edge>
	private var javaMethods: List<JavaMethod> = emptyList()
	private var longNames = false

	override fun addMenuBar(): JMenuBar {
		val menuBar = super.addMenuBar()

		// 「显示长名称」复选框
		val showLongNames = JCheckBox(NLS.str("graph_viewer.long_names"))
		showLongNames.isSelected = false
		showLongNames.addItemListener {
			longNames = showLongNames.isSelected
			reload()
		}

		val menuBarPanel = JPanel()
		menuBarPanel.isOpaque = false
		menuBarPanel.setLayout(WrapLayout(FlowLayout.LEFT))
		menuBarPanel.add(showLongNames, BorderLayout.PAGE_START)

		menuBar.add(menuBarPanel)
		return menuBar
	}

	fun reload() {
		SwingUtilities.invokeLater { getPanel().setGraph(generateGraph(cls)) }
	}

	private fun generateGraph(classNode: ClassNode): String {
		val themeBackground = UIManager.getColor("Panel.background")
		val themeForeground = UIManager.getColor("Label.foreground")
		val themeShade = UIManager.getColor("TextArea.background")

		val bgColor = "bgcolor=" + DotGraphUtils.formatColor(themeBackground)
		val lineColor = "color=" + DotGraphUtils.formatColor(themeForeground)
		val fontColor = "fontcolor=" + DotGraphUtils.formatColor(themeForeground)
		val shadeColor = "fillcolor=" + DotGraphUtils.formatColor(themeShade)

		return Formatter(StringBuilder()).use { f ->
			// 图头部
			f.format("digraph G {\n")
			f.format("%s\n", bgColor)
			f.format("node[shape=\"record\" style=\"filled\" %s %s %s %s]\n", FONT, fontColor, lineColor, shadeColor)
			f.format("edge[arrowtail=\"onormal\" arrowhead=\"onormal\" %s %s %s]\n", FONT, fontColor, lineColor)

			nextNodeID = 0
			methodToNodeID = HashMap()
			edges = HashSet()

			val methods: List<MethodNode> = classNode.methods
			javaMethods = methods.mapNotNull { it.javaNode }
			for (javaMethod in javaMethods) {
				addNode(f, javaMethod)
				// 添加 caller 关系
				addCallers(0, f, javaMethod)
			}
			// 结束图
			f.format("}")
			f.toString()
		}
	}

	private fun addCallers(depth: Int, f: Formatter, javaMethod: JavaMethod) {
		if (depth >= CALLER_DEPTH_LIMIT) {
			return
		}
		val uses: List<JavaNode> = javaMethod.useIn
		for (node in uses) {
			if (node !is JavaMethod) {
				continue
			}
			// 只处理属于本类的方法
			if (!javaMethods.contains(node)) {
				continue
			}
			val nodeID = addNode(f, node)
			addEdge(f, nodeID, checkNotNull(methodToNodeID[javaMethod]))
			addCallers(depth + 1, f, node)
		}
	}

	/** 向图中加入方法节点，返回节点 ID（已存在则复用）。 */
	private fun addNode(f: Formatter, method: JavaMethod): Int {
		var nodeID = methodToNodeID[method]
		if (nodeID == null) {
			nodeID = nextNodeID
			nextNodeID++
			methodToNodeID[method] = nodeID
		}
		val name = DotGraphUtils.methodFormatName(method, longNames)
		f.format("Node_%d [ label=\"{%s}\"]\n", nodeID, DotGraphUtils.toDotNodeName(name))
		if (method.callsSelf()) {
			addEdge(f, nodeID, nodeID)
		}
		return nodeID
	}

	/** 加入一条去重后的边。 */
	private fun addEdge(f: Formatter, sourceID: Int, destID: Int) {
		val edge = Edge(sourceID, destID)
		if (!edges.contains(edge)) {
			f.format("Node_%d -> Node_%d\n", sourceID, destID)
			edges.add(edge)
		}
	}

	companion object {
		private const val serialVersionUID = -850803763322590708L

		private const val FONT = "fontname=\"Courier\" fontsize=12"
		private const val CALLER_DEPTH_LIMIT = 10

		fun open(window: MainWindow, node: JClass) {
			val cls = node.getCls().getClassNode()
			val graphDialog = ClassMethodGraphDialog(window, cls)
			graphDialog.addMenuBar()
			graphDialog.isVisible = true
			graphDialog.reload()
		}
	}
}
