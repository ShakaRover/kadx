package jadx.gui.plugins.quark

import com.beust.jcommander.Strings
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import jadx.api.JavaClass
import jadx.api.JavaMethod
import jadx.core.utils.Utils
import jadx.gui.JadxWrapper
import jadx.gui.treemodel.JMethod
import jadx.gui.ui.MainWindow
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.JNodeCache
import jadx.gui.utils.ui.NodeLabel
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.IdentityHashMap
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * Quark 分析报告内容面板：顶部标题 + 犯罪活动树。
 *
 * **做什么**：把 [QuarkReportData] 渲染成可展开的树，点击方法节点可跳转到代码；
 * 使用缓存渲染器避免重复构建 Swing 组件。
 *
 * **线程模型**：全部在 EDT 上执行，保持原 Swing 模型。
 */
class QuarkReportPanel(
	panel: TabbedPane,
	node: QuarkReportNode,
	private val data: QuarkReportData,
) : ContentPanel(panel, node) {

	private val nodeCache: JNodeCache = panel.getMainWindow().getCacheObject().nodeCache

	private lateinit var header: JEditorPane
	private lateinit var tree: JTree
	private lateinit var treeRoot: DefaultMutableTreeNode
	private var font: Font? = null
	private var boldFont: Font? = null
	private lateinit var cellRenderer: CachingTreeCellRenderer

	init {
		prepareData()
		initUI()
		loadSettings()
	}

	private fun prepareData() {
		checkNotNull(data.crimes).sortWith(compareByDescending<QuarkReportData.Crime> { it.parseConfidence() })
	}

	private fun initUI() {
		layout = BorderLayout()

		header = JEditorPane()
		header.setContentType("text/html")
		header.setEditable(false)
		header.setText(buildHeader())

		cellRenderer = CachingTreeCellRenderer()
		treeRoot = TextTreeNode("Potential Malicious Activities:").bold()
		tree = buildTree()
		for (crime in checkNotNull(data.crimes)) {
			treeRoot.add(CrimeTreeNode(crime))
		}
		tree.expandRow(0)
		tree.expandRow(1)

		val tableScroll = JScrollPane(tree)
		tableScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)

		val mainPanel = JPanel()
		mainPanel.layout = BorderLayout()
		mainPanel.add(header, BorderLayout.PAGE_START)
		mainPanel.add(tableScroll, BorderLayout.CENTER)

		add(mainPanel)
	}
	private fun buildTree(): JTree {
		val jTree = JTree(treeRoot)
		jTree.layout = BorderLayout()
		jTree.border = BorderFactory.createEmptyBorder()
		jTree.setShowsRootHandles(false)
		jTree.setScrollsOnExpand(false)
		jTree.setSelectionModel(null)
		jTree.setCellRenderer(cellRenderer)
		jTree.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(event: MouseEvent) {
				if (SwingUtilities.isLeftMouseButton(event)) {
					val node = getNodeUnderMouse(jTree, event)
					if (node is MethodTreeNode) {
						tabsController.codeJump(node.jMethod)
					}
				}
			}
		})
		jTree.addTreeExpansionListener(object : TreeExpansionListener {
			override fun treeExpanded(event: TreeExpansionEvent) {
				val path = event.path
				val leaf = path.lastPathComponent
				if (leaf is CrimeTreeNode) {
					val children = leaf.children()
					while (children.hasMoreElements()) {
						val child = children.nextElement()
						jTree.expandPath(path.pathByAddingChild(child))
					}
				}
			}

			override fun treeCollapsed(event: TreeExpansionEvent) {
				// 无需处理
			}
		})
		return jTree
	}
	private fun buildHeader(): String {
		val builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4)
		builder.append("<h1>Quark Analysis Report</h1>")
		builder.append("<h3>")
		builder.append("File: ").append(data.apk_filename)
		builder.append("<br>")
		builder.append("Treat level: ").append(data.threat_level)
		builder.append("<br>")
		builder.append("Total score: ").append(Integer.toString(data.total_score))
		builder.append("</h3>")
		return builder.toString()
	}

	override fun loadSettings() {
		val settingsFont = mainWindow.getSettings().codeFont
		val newFont = settingsFont.deriveFont(settingsFont.getSize2D() + 1.0f)
		font = newFont
		boldFont = newFont.deriveFont(Font.BOLD)
		header.setFont(newFont)
		tree.setFont(newFont)
		cellRenderer.clearCache()
	}
	fun resolveMethod(descr: String): MutableTreeNode {
		return try {
			val parts = removeQuotes(descr).split(" ", limit = 3)
			val cls = Utils.cleanObjectName(parts[0].replace('$', '.'))
			val mth = parts[1] + parts[2].replace(" ", "")
			val mainWindow: MainWindow = mainWindow
			val wrapper: JadxWrapper = mainWindow.getWrapper()
			val javaClass: JavaClass? = wrapper.searchJavaClassByRawName(cls)
			if (javaClass == null) {
				return TextTreeNode("$cls.$mth")
			}
			val javaMethod: JavaMethod? = javaClass.searchMethodByShortId(mth)
			if (javaMethod == null) {
				return TextTreeNode(javaClass.getFullName() + "." + mth)
			}
			MethodTreeNode(javaMethod)
		} catch (e: Exception) {
			LOG.error("Failed to parse method descriptor string: {}", descr, e)
			TextTreeNode(descr)
		}
	}

	companion object {
		private const val serialVersionUID = -242266836695889206L

		private val LOG = LoggerFactory.getLogger(QuarkReportPanel::class.java)

		private fun getNodeUnderMouse(tree: JTree, mouseEvent: MouseEvent): Any? {
			val path = tree.getPathForLocation(mouseEvent.getX(), mouseEvent.getY())
			return path?.lastPathComponent
		}

		private fun removeQuotes(descr: String): String = if (descr[0] == '\'') descr.substring(1, descr.length - 1) else descr
	}
	private class CachingTreeCellRenderer : TreeCellRenderer {
		private val cache: MutableMap<BaseTreeNode, Component> = IdentityHashMap()

		override fun getTreeCellRendererComponent(
			tr: JTree,
			value: Any?,
			selected: Boolean,
			expanded: Boolean,
			leaf: Boolean,
			row: Int,
			focus: Boolean,
		): Component = cache.computeIfAbsent(value as BaseTreeNode) { it.render() }

		fun clearCache() {
			cache.clear()
		}
	}

	private abstract class BaseTreeNode(userObject: Any?) : DefaultMutableTreeNode(userObject) {
		abstract fun render(): Component

		companion object {
			private const val serialVersionUID = 7197501219150495889L
		}
	}
	private open inner class TextTreeNode(text: String) : BaseTreeNode(text) {

		private var isBold = false

		fun bold(): TextTreeNode {
			isBold = true
			return this
		}

		override fun render(): Component {
			val label = NodeLabel(getUserObject() as String)
			label.setFont(if (isBold) boldFont else font)
			label.setIcon(null)
			label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5))
			return label
		}
	}

	private inner class CrimeTreeNode(private val crime: QuarkReportData.Crime) : TextTreeNode(crime.crime) {

		init {
			bold()
			addDetails()
		}

		private fun addDetails() {
			add(TextTreeNode("Confidence: " + crime.confidence))
			if (Utils.notEmpty(crime.permissions)) {
				add(TextTreeNode("Permissions: " + Strings.join(", ", checkNotNull(crime.permissions))))
			}
			if (Utils.notEmpty(crime.native_api)) {
				val node = TextTreeNode("Native API")
				for (method in checkNotNull(crime.native_api)) {
					node.add(TextTreeNode(method.toString()))
				}
				add(node)
			}
			val combination = crime.combination
			if (Utils.notEmpty(combination) && checkNotNull(combination)[0] is JsonArray) {
				val combinationList = checkNotNull(combination)
				val node = TextTreeNode("Combination")
				val size = combinationList.size
				for (i in 0 until size) {
					val set = TextTreeNode("Set $i")
					val array = combinationList[i] as JsonArray
					for (ele in array) {
						val mth = ele.asString
						set.add(resolveMethod(mth))
					}
					node.add(set)
				}
				add(node)
			}
			if (Utils.notEmpty(crime.register)) {
				val node = TextTreeNode("Invocations")
				for (invokeMap in checkNotNull(crime.register)) {
					invokeMap.forEach { (key, _) -> node.add(resolveMethod(key)) }
				}
				add(node)
			}
		}

		override fun toString(): String = crime.crime
	}

	private inner class MethodTreeNode(private val mth: JavaMethod) : BaseTreeNode(mth) {

		private val jnode: JMethod = nodeCache.makeFrom(mth) as JMethod

		val jMethod: JMethod get() = jnode

		override fun render(): Component {
			val label = NodeLabel(mth.toString())
			label.setFont(font)
			label.setIcon(jnode.getIcon())
			label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5))
			return label
		}
	}
}
