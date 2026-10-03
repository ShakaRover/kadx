package jadx.gui.tree

import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.jobs.LoadTask
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JPackage
import jadx.gui.treemodel.JRoot
import jadx.gui.ui.MainWindow
import jadx.gui.utils.JNodeCache
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.Comparator
import java.util.stream.Collectors
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * 类树展开状态服务：保存/恢复用户在类树中展开的节点路径。
 *
 * **做什么**：把展开的 [TreePath] 序列化为短字符串（`c:` 类 / `p:` 包 / `t:` 逐级节点 id），
 * 项目关闭时保存、打开时在后台线程恢复，并在 EDT 上触发树展开。
 *
 * **为什么要过滤子路径**：只保存“最外层”的展开节点即可覆盖其所有后代，
 * 因此按路径长度倒序排序后剔除是已保存路径后代的项。
 *
 * **线程模型（阶段 5.1 保持不变）**：恢复过程使用 [LoadTask] 在后台加载，
 * 通过 [UiUtils.uiRunAndWait] 回到 EDT 执行展开，未引入协程。
 */
class TreeExpansionService(private val mainWindow: MainWindow, private val tree: JTree) {

	private val nodeCache: JNodeCache = mainWindow.getCacheObject().getNodeCache()

	/** 收集当前展开的节点路径并序列化为字符串列表；无展开或未打开项目时返回空列表。 */
	fun save(): List<String> {
		if (tree.getRowCount() == 0 || mainWindow.getWrapper().getCurrentDecompiler() == null) {
			return Collections.emptyList()
		}
		val expandedPaths = collectExpandedPaths(tree)
		val list = ArrayList<String>()
		for (expandedPath in expandedPaths) {
			list.add(savePath(expandedPath))
		}
		if (DEBUG) {
			LOG.debug("Saving tree expansions:\n {}", Utils.listToString(list, "\n "))
		}
		return list
	}

	/** 在后台恢复展开路径，并在 EDT 上展开树。 */
	fun load(treeExpansions: List<String>) {
		mainWindow.getBackgroundExecutor().execute(
			LoadTask(
				{
					val expandedPaths = ArrayList<TreePath>()
					loadPaths(treeExpansions, expandedPaths)
					// 发送展开事件以加载子节点并等待完成
					UiUtils.uiRunAndWait {
						expandedPaths.forEach { path ->
							try {
								tree.fireTreeWillExpand(path)
							} catch (e: Exception) {
								throw JadxRuntimeException("Tree expand error", e)
							}
						}
					}
					expandedPaths
				},
				{ expandedPaths ->
					// 加载任务完成后再展开路径
					expandedPaths.forEach { path -> tree.expandPath(path) }
				},
			),
		)
	}

	/** 逐个解析保存的路径字符串，失败的条目仅记录警告。 */
	private fun loadPaths(treeExpansions: List<String>, expandedPaths: MutableList<TreePath>) {
		if (DEBUG) {
			LOG.debug("Restoring tree expansions:\n {}", Utils.listToString(treeExpansions, "\n "))
		}
		for (treeExpansion in treeExpansions) {
			try {
				val treePath = loadPath(treeExpansion)
				if (treePath != null) {
					expandedPaths.add(treePath)
				}
			} catch (e: Exception) {
				LOG.warn("Failed to load tree expansion entry: {}", treeExpansion, e)
			}
		}
		if (DEBUG) {
			LOG.debug("Restored expanded tree paths:\n {}", Utils.listToString(expandedPaths, "\n "))
		}
	}

	/** 把单条展开路径序列化：包用 `p:`、类用 `c:`、其他用 `t:` + 逐级节点 id。 */
	private fun savePath(path: TreePath): String {
		val node = path.getLastPathComponent() as JNode
		if (node is JPackage) {
			return "p:" + checkNotNull(node.getPkg()).getRawFullName()
		}
		if (node is JClass) {
			return "c:" + node.getCls().getRawName()
		}
		return path.getPath()
			.map { p -> (p as JNode).getID() }
			.drop(1) // 跳过根节点
			.joinToString("//", "t:", "")
	}

	/** 按首字符类型解析单条路径；未知类型抛出异常。 */
	private fun loadPath(pathStr: String): TreePath? {
		val pathData = pathStr.substring(2)
		return when (pathStr[0]) {
			'c' -> getTreePathForRef(getRoot().resolveRawClass(pathData))
			'p' -> getTreePathForRef(getRoot().resolvePackage(pathData))
			't' -> resolveTreePath(pathData.split("//"))
			else -> throw JadxRuntimeException("Unknown tree expansion path type: $pathStr")
		}
	}

	/** 从根节点开始逐级查找节点 id，构造完整 [TreePath]。 */
	private fun resolveTreePath(pathArr: List<String>): TreePath? {
		var current = tree.getModel().getRoot() as JNode
		for (nodeStr in pathArr) {
			val node = current.searchNode { n -> n.getID() == nodeStr }
			if (node == null) {
				if (DEBUG) {
					val children = current.childrenList().stream()
						.map { n -> (n as JNode).getID() }
						.collect(Collectors.toList())
					LOG.warn(
						"Failed to restore path: {}, node '{}' not found in '{}' children: {}",
						pathArr,
						nodeStr,
						current,
						children,
					)
				}
				return null
			}
			current = node
		}
		return TreePath(current.getPath())
	}

	/** 由代码节点引用定位其在树中的路径；节点不在树中时尝试从根搜索。 */
	private fun getTreePathForRef(ref: ICodeNodeRef?): TreePath? {
		if (ref == null) {
			return null
		}
		var node = nodeCache.makeFrom(ref) ?: return null
		if (node.getParent() == null) {
			if (DEBUG) {
				LOG.warn("Resolving node not from tree: {}", node)
			}
			val treeNode = (tree.getModel().getRoot() as JRoot).searchNode(node)
			if (treeNode == null) {
				if (DEBUG) {
					LOG.error("Node not found in tree: {}", node)
				}
				return null
			}
			node = treeNode
		}
		val pathNodes = (tree.getModel() as DefaultTreeModel).getPathToRoot(node)
		if (pathNodes == null) {
			return null
		}
		return TreePath(pathNodes)
	}

	/** 收集展开路径并剔除子路径（只保留最外层展开节点）。 */
	private fun collectExpandedPaths(tree: JTree): List<TreePath> {
		val root = tree.getPathForRow(0)
		val expandedDescendants = tree.getExpandedDescendants(root)
		if (expandedDescendants == null) {
			return Collections.emptyList()
		}
		val expandedPaths = ArrayList<TreePath>()
		while (expandedDescendants.hasMoreElements()) {
			val path = expandedDescendants.nextElement()
			if (path.getPathCount() > 1) {
				expandedPaths.add(path)
			}
		}
		// 过滤掉子路径
		expandedPaths.sortWith(PATH_LENGTH_REVERSE) // 最长路径排在子路径之前
		val result = ArrayList<TreePath>()
		for (path in expandedPaths) {
			if (!isSubPath(result, path)) {
				result.add(path)
			}
		}
		return result
	}

	/** [path] 是否为 [paths] 中任一路径的后代。 */
	private fun isSubPath(paths: List<TreePath>, path: TreePath): Boolean {
		for (addedPath in paths) {
			if (path.isDescendant(addedPath)) {
				return true
			}
		}
		return false
	}

	private fun getRoot(): RootNode = checkNotNull(mainWindow.getWrapper().getDecompiler().getRoot())

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TreeExpansionService::class.java)
		private const val DEBUG = false

		/** 按路径长度倒序比较，使最长路径排在前面。 */
		private val PATH_LENGTH_REVERSE: Comparator<TreePath> =
			Comparator.comparingInt<TreePath> { p -> -p.getPathCount() }
	}
}
