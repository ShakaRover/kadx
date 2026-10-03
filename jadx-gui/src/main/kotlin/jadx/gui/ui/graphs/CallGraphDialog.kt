package jadx.gui.ui.graphs

import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.core.dex.info.MethodInfo
import jadx.core.utils.DotGraphUtils
import jadx.gui.treemodel.JMethod
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.layout.WrapLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
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
 * 方法调用图：以方法为节点，展示 caller / callee 关系。
 *
 * **做什么**：从目标方法出发分别向 caller（`getUseIn`）和 callee（`getUsed` / `getUnresolvedUsed`）
 * 方向递归，深度可分别调节；未解析到的目标方法用虚线节点表示。
 *
 * **线程模型**：保持原 Swing 模型，[reload] 用 `SwingUtilities.invokeLater` 更新图形。
 */
class CallGraphDialog(mainWindow: MainWindow, private val javaMethod: JavaMethod) :
	GraphDialog(
		mainWindow,
		"${NLS.str("graph_viewer.call_graph.title")}: ${DotGraphUtils.methodFormatName(javaMethod, false)}",
	) {

	private var callerDepthLimit = 3
	private var calleeDepthLimit = 3
	private var nextNodeID = 0
	private lateinit var methodToNodeID: MutableMap<JavaMethod, Int>
	private lateinit var unresolvedMethodToNodeID: MutableMap<MethodInfo, Int>
	private lateinit var edges: MutableSet<Edge>
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

		// callee 深度调节
		val calleeDepthSpinnerModel = SpinnerNumberModel(3, 0, 100, 1)
		val calleeDepthSpinner = JSpinner(calleeDepthSpinnerModel)
		calleeDepthSpinner.addChangeListener {
			calleeDepthLimit = calleeDepthSpinner.value as Int
			reload()
		}
		val calleeLbl = JLabel(NLS.str("graph_viewer.callee_depth"))
		calleeLbl.setLabelFor(calleeDepthSpinner)
		calleeLbl.setHorizontalAlignment(SwingConstants.LEFT)
		val calleePanel = JPanel()
		calleePanel.isOpaque = false
		calleePanel.setLayout(BoxLayout(calleePanel, BoxLayout.LINE_AXIS))
		calleePanel.add(calleeLbl)
		calleePanel.add(Box.createRigidArea(Dimension(3, 0)))
		calleePanel.add(calleeDepthSpinner)

		// caller 深度调节
		val callerDepthSpinnerModel = SpinnerNumberModel(3, 0, 100, 1)
		val callerDepthSpinner = JSpinner(callerDepthSpinnerModel)
		callerDepthSpinner.addChangeListener {
			callerDepthLimit = callerDepthSpinner.value as Int
			reload()
		}
		val callerLbl = JLabel(NLS.str("graph_viewer.caller_depth"))
		callerLbl.setLabelFor(callerDepthSpinner)
		callerLbl.setHorizontalAlignment(SwingConstants.LEFT)
		val callerPanel = JPanel()
		callerPanel.isOpaque = false
		callerPanel.setLayout(BoxLayout(callerPanel, BoxLayout.LINE_AXIS))
		callerPanel.add(callerLbl)
		callerPanel.add(Box.createRigidArea(Dimension(3, 0)))
		callerPanel.add(callerDepthSpinner)

		val menuBarPanel = JPanel()
		menuBarPanel.isOpaque = false
		menuBarPanel.setLayout(WrapLayout(FlowLayout.LEFT))
		menuBarPanel.add(showLongNames, BorderLayout.PAGE_START)
		menuBarPanel.add(Box.createRigidArea(Dimension(10, 0)))
		menuBarPanel.add(calleePanel)
		menuBarPanel.add(Box.createRigidArea(Dimension(10, 0)))
		menuBarPanel.add(callerPanel)

		menuBar.add(menuBarPanel)
		return menuBar
	}

	fun reload() {
		SwingUtilities.invokeLater {
			val graph = generateGraph(javaMethod)
			getPanel().setGraph(graph)
		}
	}

	private fun generateGraph(javaMethod: JavaMethod): String {
		val sb = StringBuilder()
		val themeBackground = UIManager.getColor("Panel.background")
		val themeForeground = UIManager.getColor("Label.foreground")
		val themeHighlight = UIManager.getColor("Component.focusedBorderColor")
		val themeShade = UIManager.getColor("TextArea.background")

		val bgColor = String.format("bgcolor=\"#%02x%02x%02x\"", themeBackground.red, themeBackground.green, themeBackground.blue)
		val lineColor = String.format("color=\"#%02x%02x%02x\"", themeForeground.red, themeForeground.green, themeForeground.blue)
		val fontColor = String.format("fontcolor=\"#%02x%02x%02x\"", themeForeground.red, themeForeground.green, themeForeground.blue)
		val highlightColor = String.format("color=\"#%02x%02x%02x\"", themeHighlight.red, themeHighlight.green, themeHighlight.blue)
		val shadeColor = String.format("fillcolor=\"#%02x%02x%02x\"", themeShade.red, themeShade.green, themeShade.blue)

		return Formatter(sb).use { f ->
			// 图头部
			f.format("digraph G {\n")
			f.format("%s\n", bgColor)
			f.format("node[shape=\"record\" style=\"filled\" %s %s %s %s]\n", FONT, fontColor, lineColor, shadeColor)
			f.format("edge[arrowtail=\"onormal\" arrowhead=\"onormal\" %s %s %s]\n", FONT, fontColor, lineColor)

			nextNodeID = 0
			methodToNodeID = HashMap()
			unresolvedMethodToNodeID = HashMap()
			edges = HashSet()

			addNode(f, javaMethod, highlightColor)
			// 添加 caller / callee 关系
			addCallers(0, f, javaMethod)
			addCallees(0, f, javaMethod)
			f.format("}")
			f.toString()
		}
	}

	private fun addCallers(depth: Int, f: Formatter, javaMethod: JavaMethod) {
		if (depth >= callerDepthLimit) {
			return
		}
		val uses: List<JavaNode> = javaMethod.getUseIn()
		for (node in uses) {
			if (node !is JavaMethod) {
				continue
			}
			val nodeID = addNode(f, node)
			addEdge(f, nodeID, checkNotNull(methodToNodeID[javaMethod]))
			addCallers(depth + 1, f, node)
		}
	}

	private fun addCallees(depth: Int, f: Formatter, javaMethod: JavaMethod) {
		if (depth >= calleeDepthLimit) {
			return
		}
		val used: List<JavaNode> = javaMethod.getUsed()
		for (node in used) {
			if (node !is JavaMethod) {
				continue
			}
			val nodeID = addNode(f, node)
			addEdge(f, checkNotNull(methodToNodeID[javaMethod]), nodeID)
			addCallees(depth + 1, f, node)
		}
		addUnresolvedCallees(depth, f, javaMethod)
	}

	private fun addUnresolvedCallees(depth: Int, f: Formatter, javaMethod: JavaMethod) {
		if (depth >= calleeDepthLimit) {
			return
		}
		// 原 Java 对 callee.getName() 判空，但 MethodInfo.name 非空，故该分支为死代码
		for (callee in javaMethod.getUnresolvedUsed()) {
			val nodeID = addNode(f, callee)
			addEdge(f, checkNotNull(methodToNodeID[javaMethod]), nodeID)
		}
	}

	private fun addNode(f: Formatter, method: JavaMethod): Int = addNode(f, method, "")

	/** 向图中加入方法节点，返回节点 ID（已存在则复用）。 */
	private fun addNode(f: Formatter, method: JavaMethod, extra: String): Int {
		var nodeID = methodToNodeID[method]
		if (nodeID == null) {
			nodeID = nextNodeID
			nextNodeID++
			methodToNodeID[method] = nodeID
		}
		val name = DotGraphUtils.methodFormatName(method, longNames)
		f.format("Node_%d [ label=\"{%s}\" %s]\n", nodeID, DotGraphUtils.toDotNodeName(name), extra)
		if (javaMethod.callsSelf()) {
			addEdge(f, nodeID, nodeID)
		}
		return nodeID
	}

	private fun addNode(f: Formatter, method: MethodInfo): Int = addNode(f, method, "")

	/** 向图中加入未解析方法节点（虚线），返回节点 ID（已存在则复用）。 */
	private fun addNode(f: Formatter, method: MethodInfo, extra: String): Int {
		var nodeID = unresolvedMethodToNodeID[method]
		if (nodeID == null) {
			nodeID = nextNodeID
			nextNodeID++
			unresolvedMethodToNodeID[method] = nodeID
		}
		val name = DotGraphUtils.unresolvedMethodFormatName(method, longNames)
		val themeOutOfFocus = UIManager.getColor("Component.disabledBorderColor")
		val outOfFocus = "color=" + DotGraphUtils.formatColor(themeOutOfFocus)
		f.format("Node_%d [ label=\"{%s}\" style=dashed %s %s]\n", nodeID, DotGraphUtils.toDotNodeName(name), outOfFocus, extra)
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

		@JvmStatic
		fun open(window: MainWindow, method: JMethod) {
			val javaMethod = method.getJavaMethod()
			val graphDialog = CallGraphDialog(window, javaMethod)
			graphDialog.addMenuBar()
			graphDialog.isVisible = true
			graphDialog.reload()
		}
	}
}
