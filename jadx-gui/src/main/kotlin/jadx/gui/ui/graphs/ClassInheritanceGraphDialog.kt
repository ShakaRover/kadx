package jadx.gui.ui.graphs

import com.android.apksig.internal.util.Pair
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.MethodOverrideAttr
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.utils.DotGraphUtils
import jadx.core.utils.StringUtils
import jadx.gui.treemodel.JClass
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.layout.WrapLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.ArrayList
import java.util.Formatter
import javax.swing.JCheckBox
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * 类继承图：展示类/接口之间的 extends / implements 关系。
 *
 * **做什么**：从根类递归展开接口与父类；可切换是否显示长类名，
 * 以及是否为每个方法列出它覆写的基类方法（override 详情）。
 *
 * **线程模型**：保持原 Swing 模型，[reload] 用 `SwingUtilities.invokeLater` 更新图形。
 */
class ClassInheritanceGraphDialog(mainWindow: MainWindow, private val cls: ClassNode) :
	GraphDialog(
		mainWindow,
		"${NLS.str("graph_viewer.inheritance_graph.title")}: ${DotGraphUtils.classFormatName(cls, false)}",
	) {

	private var longNames = false
	private var overrides = false

	private var objectToNodeID: MutableMap<Any, Int> = HashMap()
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

		val menuBarPanel = JPanel()
		menuBarPanel.isOpaque = false
		menuBarPanel.setLayout(WrapLayout(FlowLayout.LEFT))
		menuBarPanel.add(showLongNames, BorderLayout.PAGE_START)
		menuBarPanel.add(showOverrides, BorderLayout.PAGE_START)

		menuBar.add(menuBarPanel)
		return menuBar
	}

	fun reload() {
		SwingUtilities.invokeLater {
			val graph = generateGraph(cls)
			getPanel().setGraph(graph)
		}
	}

	private fun generateGraph(rootClass: ClassNode): String {
		objectToNodeID = HashMap()

		val themeBackground = UIManager.getColor("Panel.background")
		val themeForeground = UIManager.getColor("Label.foreground")
		val themeHighlight = UIManager.getColor("Component.focusedBorderColor")
		val themeShade = UIManager.getColor("TextArea.background")

		val bgColor = "bgcolor=" + DotGraphUtils.formatColor(themeBackground)
		val lineColor = "color=" + DotGraphUtils.formatColor(themeForeground)
		val fontColor = "fontcolor=" + DotGraphUtils.formatColor(themeForeground)
		val highlightColor = "color=" + DotGraphUtils.formatColor(themeHighlight)
		val shadeColor = "fillcolor=" + DotGraphUtils.formatColor(themeShade)

		val sb = StringBuilder()
		return Formatter(sb).use { f ->
			// 图头部
			f.format("digraph G {\n")
			f.format("%s\n", bgColor)
			f.format("node[shape=\"record\" style=\"filled\" %s %s %s %s]\n", FONT, fontColor, lineColor, shadeColor)
			f.format("edge[arrowtail=\"onormal\" arrowhead=\"onormal\" %s %s %s]\n", FONT, fontColor, lineColor)

			// 添加节点
			processClass(f, rootClass, highlightColor)
			f.format("}")
			f.toString()
		}
	}

	private fun processClass(f: Formatter, cls: ClassNode): Int = processClass(f, cls, "")

	private fun processClass(f: Formatter, cls: ClassNode, extra: String): Int {
		if (objectToNodeID.containsKey(cls)) {
			// 已处理过的类不再展开
			return checkNotNull(objectToNodeID[cls])
		}
		val classID = addNode(f, cls, extra)

		// 接口关系
		for (iface in cls.interfaces) {
			val ifaceID: Int
			val ifaceNode = cls.root.resolveClass(iface)
			if (ifaceNode != null) {
				ifaceID = processClass(f, ifaceNode)
				objectToNodeID[iface] = ifaceID
			} else {
				ifaceID = addNode(f, iface)
			}
			// 类实现接口，接口继承接口
			val edgeLabel = if (cls.accessFlags.isInterface()) "extends" else "implements"
			f.format("Node_%d -> Node_%d [label=\"%s\" style=\"dashed\" ]\n", classID, ifaceID, edgeLabel)
		}
		// 父类关系
		val superClass = cls.superClass
		if (superClass !== ArgType.OBJECT && superClass != null) {
			val superClsID: Int
			val resolvedSuperCls = cls.root.resolveClass(superClass)
			if (resolvedSuperCls != null) {
				superClsID = processClass(f, resolvedSuperCls)
				objectToNodeID[superClass] = superClsID
			} else {
				superClsID = addNode(f, superClass)
			}
			f.format("Node_%d -> Node_%d [label=\"extends\" ]\n", classID, superClsID)
		}
		return classID
	}

	private fun addNode(f: Formatter, cls: ClassNode): Int = addNode(f, cls, "")

	/** 加入一个类节点（接口用虚线填充样式）。 */
	private fun addNode(f: Formatter, cls: ClassNode, extra: String): Int {
		var nodeID = objectToNodeID[cls]
		if (nodeID == null) {
			nodeID = nextNodeID
			nextNodeID++
			objectToNodeID[cls] = nodeID
		}
		var extraText = extra
		if (cls.accessFlags.isInterface()) {
			extraText += " style=\"dashed, filled\""
		}
		val name = DotGraphUtils.classFormatName(cls, longNames)
		f.format("Node_%d [ label=\"{%s\\ ", nodeID, DotGraphUtils.toDotNodeName(name))
		if (overrides) {
			f.format("|")
			val table: MutableList<Pair<String, String>> = ArrayList()
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
					table.add(Pair.of(methodName, detailsString))
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
		f.format("}\" %s]\n", extraText)
		return nodeID
	}

	private fun addNode(f: Formatter, argType: ArgType): Int = addNode(f, argType, "")

	/** 加入一个未解析类型的节点（置灰）。 */
	private fun addNode(f: Formatter, argType: ArgType, extra: String): Int {
		var nodeID = objectToNodeID[argType]
		if (nodeID == null) {
			nodeID = nextNodeID
			nextNodeID++
			objectToNodeID[argType] = nodeID
		}
		val themeOutOfFocus = UIManager.getColor("Component.disabledBorderColor")
		val outOfFocus = "color=" + DotGraphUtils.formatColor(themeOutOfFocus)
		val name = DotGraphUtils.interfaceFormatName(argType, cls, longNames)
		f.format("Node_%d [ label=\"{%s}\" %s %s]\n", nodeID, DotGraphUtils.toDotNodeName(name), outOfFocus, extra)
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
