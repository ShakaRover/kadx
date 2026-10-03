package jadx.gui.ui.graphs

import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.utils.DotGraphUtils
import jadx.gui.treemodel.JMethod
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.layout.WrapLayout
import java.awt.FlowLayout
import java.util.HashMap
import javax.swing.Box
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JMenuBar
import javax.swing.JPanel

/** 预设名，顺序必须与 [ControlFlowGraphDialog.GraphPreset] 常量一致。 */
private val PRESETS_NSL: List<String> = NLS.str("graph_viewer.cfg.preset_names").split("|")

/**
 * 控制流图（CFG）对话框：按指定的 Pass 阶段展示方法的控制流。
 *
 * **做什么**：用 [GraphPreset] 选择「原始指令 / 常规 / 区域」三种视角，
 * 也可以手动选择在某个 Pass 之前停下来 dump 控制流图。
 *
 * **线程模型**：保持原 Swing 模型，[reloadGraph] 用 [UiUtils.uiRun] 回到 EDT。
 */
class ControlFlowGraphDialog private constructor(mainWindow: MainWindow, jMth: JMethod) : GraphDialog(mainWindow) {

	private val mth: MethodNode = jMth.getJavaMethod().getMethodNode()
	private var graphPreset: GraphPreset? = null
	private lateinit var presetsCB: JComboBox<GraphPreset>
	private lateinit var passesCB: JComboBox<String>
	private lateinit var passNames: Array<String>
	private var currentPassIdx = 0

	init {
		val mthName = DotGraphUtils.methodFormatName(jMth.getJavaMethod(), false)
		title = "${NLS.str("graph_viewer.cfg.title")}: $mthName"
	}

	/** CFG 展示预设：控制是否使用原始指令、是否按区域着色，以及在哪个 Pass 前 dump。 */
	private enum class GraphPreset(
		private val nlsStr: String,
		val useRawInsns: Boolean,
		val useRegions: Boolean,
		val beforePass: String,
	) {
		RAW(PRESETS_NSL[0], true, false, "SSATransform"),
		NORMAL(PRESETS_NSL[1], false, false, "RegionMakerVisitor"),
		REGION(PRESETS_NSL[2], false, true, "PrepareForCodeGen"),
		;

		override fun toString(): String = nlsStr
	}

	private fun usePreset(graphPreset: GraphPreset?) {
		if (graphPreset == null || this.graphPreset === graphPreset) {
			return
		}
		this.graphPreset = graphPreset
		this.currentPassIdx = selectPassBefore(graphPreset.beforePass)
		presetsCB.setSelectedItem(graphPreset)
		passesCB.setSelectedItem(passNames[currentPassIdx])
		reloadGraph()
	}

	private fun selectPassBefore(beforePass: String): Int {
		val passes = getPassList()
		for (i in 1 until passes.size) {
			if (passes[i].getName() == beforePass) {
				return i - 1
			}
		}
		return passes.size - 1
	}

	override fun addMenuBar(): JMenuBar {
		val menuBar = super.addMenuBar()
		presetsCB = JComboBox(GraphPreset.values())
		presetsCB.addActionListener { usePreset(presetsCB.selectedItem as? GraphPreset) }

		val passList = getPassList()
		val size = passList.size
		val passMap: MutableMap<String, Int> = HashMap()
		passNames = Array(size) { "" }
		for (i in 0 until size) {
			val pass = passList[i]
			val name = "$i: " + pass.getName()
			passMap[name] = i
			passNames[i] = name
		}
		passesCB = JComboBox(passNames)
		passesCB.addActionListener {
			val newValue = passesCB.selectedItem as? String
			if (newValue != null) {
				val newIdx = passMap[newValue]
				if (newIdx != null && newIdx != currentPassIdx) {
					currentPassIdx = newIdx
					reloadGraph()
				}
			}
		}

		val menuBarPanel = JPanel()
		menuBarPanel.isOpaque = false
		menuBarPanel.setLayout(WrapLayout(FlowLayout.LEFT))
		menuBarPanel.add(JLabel(NLS.str("graph_viewer.cfg.preset_selector_label")))
		menuBarPanel.add(presetsCB)
		menuBarPanel.add(Box.createHorizontalBox())
		menuBarPanel.add(JLabel(NLS.str("graph_viewer.cfg.pass_selector_label")))
		menuBarPanel.add(passesCB)
		menuBar.add(menuBarPanel)
		return menuBar
	}

	internal override fun disableMenu() {
		// 指令类型与 Pass 组合非法时不关闭菜单
	}

	private fun reloadGraph() {
		UiUtils.uiRun {
			val graph = generateGraph()
			if (graph != null) {
				getPanel().setGraph(graph)
			} else {
				getPanel().invalidateImage(GraphDialog.graphError(NLS.str("graph_viewer.default_error")))
			}
		}
	}

	private fun generateGraph(): String? {
		val preset = graphPreset ?: return null
		try {
			val pass = getPassList()[currentPassIdx]
			val success = mth.root().getProcessClasses().processMethodToVisitor(mth, pass)
			if (!success) {
				return null
			}
			return DotGraphUtils(preset.useRegions, preset.useRawInsns).dumpToString(mth)
		} finally {
			mth.unload()
		}
	}

	private fun getPassList(): List<IDexTreeVisitor> = mth.root().getProcessClasses().getPasses()

	companion object {
		private const val serialVersionUID = -68749445239697710L

		@JvmStatic
		fun open(window: MainWindow, jMth: JMethod) {
			val graphDialog = ControlFlowGraphDialog(window, jMth)
			graphDialog.addMenuBar()
			graphDialog.isVisible = true
			graphDialog.usePreset(GraphPreset.NORMAL)
		}
	}
}
