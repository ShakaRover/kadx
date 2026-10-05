package kadx.gui.treemodel

import kadx.core.utils.files.FileUtils
import kadx.gui.settings.KadxProject
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.gui.utils.plugins.TreeInputsHelper
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * 输入文件根节点（“输入”子树）。
 *
 * **做什么**：展开项目配置中的所有输入路径，并按插件规则（[TreeInputsHelper]）
 * 生成普通文件节点与自定义分类节点。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class JInputs(mainWindow: MainWindow) : JNode() {

	init {
		val project: KadxProject = mainWindow.getProject()
		val inputs = project.filePaths
		val files = FileUtils.expandDirs(inputs)
		val inputsHelper = TreeInputsHelper(mainWindow)
		inputsHelper.processInputs(files)
		add(JInputFiles(inputsHelper.getSimpleFiles()))
		inputsHelper.customNodes.forEach { add(it) }
	}

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon = INPUTS_ICON

	override fun getID(): String = "JInputs"

	override fun makeString(): String = NLS.str("tree.inputs_title")

	companion object {
		private val INPUTS_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/projectStructure")
	}
}
