package jadx.gui.utils.plugins

import jadx.gui.plugins.context.ITreeInputCategory
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * 输入文件分类辅助器。
 *
 * **做什么**：在构建「输入」树时，让插件注册的 [ITreeInputCategory] 有机会把
 * 匹配的文件收进自定义分类节点；未匹配的文件保留为普通文件节点。
 *
 * **流程**：构造时从 GUI 插件上下文取出所有分类器；
 * [processInputs] 逐个文件询问分类器，[getCustomNodes] 为每个分类器生成节点，
 * [getSimpleFiles] 返回未被任何分类器认领的文件。
 */
class TreeInputsHelper(mainWindow: MainWindow) {

	private val categoryData: List<CategoryData> = mainWindow.getWrapper().getGuiPluginsContext()
		.getTreeInputCategories()
		.map { CategoryData(it) }

	private var simpleFiles: MutableList<Path> = ArrayList()

	/**
	 * 把输入文件分派给各分类器；未被认领的文件进入 [simpleFiles]。
	 */
	fun processInputs(files: List<Path>) {
		simpleFiles = ArrayList(files.size)
		for (file in files) {
			var added = false
			for (data in categoryData) {
				if (data.filesFilter(file)) {
					added = true
					break
				}
			}
			if (!added) {
				simpleFiles.add(file)
			}
		}
	}

	/** 各分类器生成的节点（忽略构建失败的分类器）。 */
	fun getCustomNodes(): List<JNode> = categoryData.mapNotNull { it.buildInputNode() }

	/** 未被任何分类器认领的普通文件。 */
	fun getSimpleFiles(): List<Path> = simpleFiles

	/**
	 * 单个分类器运行时数据：持有分类器与已收集的文件。
	 */
	private class CategoryData(private val provider: ITreeInputCategory) {

		private val collectedFiles: MutableList<Path> = ArrayList()

		/** 询问分类器是否认领该文件；认领则记录并返回 true。 */
		fun filesFilter(file: Path): Boolean {
			try {
				if (provider.filesFilter(file)) {
					collectedFiles.add(file)
					return true
				}
			} catch (e: Exception) {
				LOG.error("Failed to filter input files", e)
			}
			return false
		}

		/** 让分类器为已收集的文件构建节点；失败时返回 null。 */
		fun buildInputNode(): JNode? = try {
			provider.buildInputNode(collectedFiles)
		} catch (e: Exception) {
			LOG.error("Failed to build custom input node", e)
			null
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(TreeInputsHelper::class.java)
	}
}
