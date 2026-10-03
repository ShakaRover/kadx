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
import jadx.gui.settings.JadxSettings
import jadx.gui.treemodel.CodeNode
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JField
import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.utils.ILoadListener
import jadx.gui.utils.JNodeCache
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.NodeLabel
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.util.Collections
import java.util.HashMap
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.WindowConstants

/**
 * 「查找用法」对话框：列出某个节点（类 / 方法 / 字段）被引用的位置。
 *
 * **做什么**：构造时收集用法数据，加载完成后填入结果表并高亮节点名。
 *
 * **为什么保留 Swing 线程模型**：数据收集走 [MainWindow.getBackgroundExecutor]，
 * UI 更新回到 EDT，不引入协程。
 */
class UsageDialog private constructor(
	mainWindow: MainWindow,
	private val node: JNode,
) : CommonSearchDialog(mainWindow, NLS.str("usage_dialog.title")) {

	private lateinit var usageList: MutableList<CodeNode>

	override fun openInit() {
		progressStartCommon()
		prepareUsageData()
		mainWindow.getBackgroundExecutor().execute(
			NLS.str("progress.load"),
			Runnable { collectUsageData() },
			{ status ->
				if (status == TaskStatus.CANCEL_BY_MEMORY) {
					mainWindow.showHeapUsageBar()
					UiUtils.errorMessage(this, NLS.str("message.memoryLow"))
				}
				progressFinishedCommon()
				loadFinished()
			},
		)
	}

	private fun prepareUsageData() {
		if (mainWindow.getSettings().isReplaceConsts() && node is JField) {
			val fld: FieldNode = (node as JField).getJavaField().getFieldNode()
			val constField = CollectConstValues.getFieldConstValue(fld) != null
			if (constField && !fld.accessFlags.isPrivate()) {
				// 执行完整反编译，为全量代码扫描做准备
				mainWindow.requestFullDecompilation()
			}
		}
	}

	private fun collectUsageData() {
		usageList = ArrayList()
		buildUsageQuery().forEach { (searchNode, useNodes) ->
			useNodes.stream()
				.map { it.getTopParentClass() }
				.distinct()
				.forEach { u -> processUsage(searchNode, checkNotNull(u)) }
		}
	}

	/** 返回「待搜索节点 -> 引用位置」的映射。 */
	private fun buildUsageQuery(): Map<JavaNode, List<JavaNode>> {
		val map = HashMap<JavaNode, List<JavaNode>>()
		if (node is JMethod) {
			val javaMethod: JavaMethod = (node as JMethod).getJavaMethod()
			for (mth in getMethodWithOverrides(javaMethod)) {
				map[mth] = mth.getUseIn()
			}
			return map
		}
		if (node is JClass) {
			val javaCls: JavaClass = (node as JClass).getCls()
			map[javaCls] = javaCls.getUseIn()
			// 把构造函数的引用并入类的引用
			for (javaMth in javaCls.getMethods()) {
				if (javaMth.isConstructor()) {
					map[javaMth] = javaMth.getUseIn()
				}
			}
			return map
		}
		if (node is JField && mainWindow.getSettings().isReplaceConsts()) {
			val fld: FieldNode = (node as JField).getJavaField().getFieldNode()
			val constField = CollectConstValues.getFieldConstValue(fld) != null
			if (constField && !fld.accessFlags.isPrivate()) {
				// 搜索全部类以收集被替换常量的引用
				map[checkNotNull(fld.javaNode)] = mainWindow.getWrapper().getIncludedClasses()
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

	private fun processUsage(searchNode: JavaNode, topUseClass: JavaClass) {
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
			val nodeCache: JNodeCache = getNodeCache()
			val enclosingNode = wrapper.getEnclosingNode(codeInfo, pos)
			val rootJCls: JClass = checkNotNull(nodeCache.makeFrom(topUseClass))
			val usageJNode: JNode = if (enclosingNode == null) rootJCls else checkNotNull(nodeCache.makeFrom(enclosingNode))
			usageList.add(CodeNode(rootJCls, usageJNode, line.trim(), pos))
		}
	}
	override fun loadFinished() {
		resultsTable.setEnabled(true)
		resultsModel.clear()

		Collections.sort(usageList)
		resultsModel.addAll(usageList)
		updateHighlightContext(checkNotNull(node.getName()), true, false, true)
		resultsTable.initColumnWidth()
		resultsTable.updateTable()
		updateProgressLabel(true)
	}

	override fun loadStart() {
		resultsTable.setEnabled(false)
	}

	private fun initUI() {
		val settings: JadxSettings = mainWindow.getSettings()
		val font: Font = settings.getCodeFont()
		val lbl = JLabel(NLS.str("usage_dialog.label"))
		lbl.setFont(font)
		val nodeLabel = NodeLabel.longName(node)
		nodeLabel.setFont(font)
		lbl.setLabelFor(nodeLabel)

		val searchPane = JPanel()
		searchPane.setLayout(FlowLayout(FlowLayout.LEFT))
		searchPane.add(lbl)
		searchPane.add(nodeLabel)
		searchPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

		initCommon()
		val resultsPanel = initResultsTable()
		val buttonPane = initButtonsPanel()

		val contentPanel = JPanel()
		contentPanel.setLayout(BorderLayout(5, 5))
		contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		contentPanel.add(searchPane, BorderLayout.PAGE_START)
		contentPanel.add(resultsPanel, BorderLayout.CENTER)
		contentPanel.add(buttonPane, BorderLayout.PAGE_END)
		getContentPane().add(contentPanel)

		pack()
		setSize(800, 500)
		setLocationRelativeTo(null)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
	}
	init {
		initUI()
		registerInitOnOpen()
		loadWindowPos()
	}

	companion object {
		private const val serialVersionUID = -5105405789969134105L

		/** 打开某个节点的用法对话框。 */
		@JvmStatic
		fun open(mainWindow: MainWindow, node: JNode) {
			val usageDialog = UsageDialog(mainWindow, node)
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
		}
	}
}
