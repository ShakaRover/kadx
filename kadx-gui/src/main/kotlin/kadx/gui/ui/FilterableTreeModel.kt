package kadx.gui.ui

import kadx.api.ResourceType
import kadx.gui.jobs.SimpleTask
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JMethod
import kadx.gui.treemodel.JNode
import kadx.gui.treemodel.JResource
import kadx.gui.treemodel.JRoot
import kadx.gui.treemodel.TextNode
import kadx.gui.utils.UiUtils
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Rectangle
import java.util.Enumeration
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * 支持动态过滤的反编译包树模型（PR #2941）。
 *
 * **做什么**：在模型层过滤树节点，避免“零宽行”破坏键盘导航。匹配时优先使用
 * 节点的全限定字符串（[JNode.makeString]），过滤路径上的所有中间节点也会保留。
 *
 * **线程模型**：[setFilter] 通常在后台线程调用（构建匹配集合），
 * 之后通过 `SwingUtilities.invokeLater` 通知树刷新与自动展开（EDT）。
 */
class FilterableTreeModel(
	private val mainWindow: MainWindow,
	root: TreeNode,
) : DefaultTreeModel(root) {

	/** 当前过滤字符串，空串表示未启用过滤。 */
	@Volatile
	private var filter: String = ""

	/** 所有匹配到的节点（含路径上的中间节点）。 */
	private val filteredTreeNodes: MutableSet<TreeNode> = HashSet()

	/** 记录已自动展开的节点，避免用户手动折叠后被再次展开。 */
	private val autoExpandedNodes: MutableSet<JNode> = HashSet()

	/**
	 * `getChildCount` 之后通常会连续调用 `getChild`，
	 * 缓存当前父节点的子列表以加速访问。
	 */
	private val cacheNode = CacheNode()

	/**
	 * 设置过滤字符串：预先计算匹配的树路径并刷新树结构。
	 *
	 * 这里对根节点调用 `nodeStructureChanged`，意味着整棵树完整刷新；
	 * 由于过滤是声明式的，无法做更细粒度的刷新来保留更多展开状态。
	 *
	 * @param newFilter 新的过滤字符串，空串表示取消过滤。
	 */
	@Synchronized
	fun setFilter(newFilter: String) {
		filter = newFilter
		autoExpandedNodes.clear()
		cacheNode.clear()
		LOG.debug("New tree filter '{}'", newFilter)
		applyFilterFieldOutline("")
		collectFilteredPaths()
		SwingUtilities.invokeLater { nodeStructureChanged(getRoot() as TreeNode?) }
		SwingUtilities.invokeLater { expandVisibleFilteredNodes(mainWindow.getTree()) }
	}

	// 参数必须可空：DefaultTreeModel.setRoot(null)（清空项目树）会以 null 调用本方法，
	// 上游 Java 透传给同样容忍 null 的 super 实现；Kotlin 非空声明会在空项目加载时 NPE
	override fun nodeStructureChanged(node: TreeNode?) {
		super.nodeStructureChanged(node)
	}

	/** 过滤启用时，自动展开当前可见区域内的节点。 */
	fun expandVisibleFilteredNodes(tree: JTree) {
		if (filter.isEmpty()) {
			return
		}
		val rect: Rectangle = tree.getVisibleRect()
		var startRow = tree.getClosestRowForLocation(0, rect.y)
		val bottom = rect.y + rect.height - 1
		while (true) {
			val lastRow = tree.getClosestRowForLocation(0, bottom)
			if (lastRow <= startRow) {
				break
			}
			// 限制单次迭代的更新量
			val last = Math.min(startRow + 20, lastRow)
			for (i in startRow..lastRow) {
				val path: TreePath = tree.getPathForRow(i) ?: continue
				val node = path.getLastPathComponent() as? JNode ?: continue
				if (node !is JClass) {
					// 不自动展开类节点（方法列表）
					if (autoExpandedNodes.add(node)) {
						tree.expandPath(path)
					}
				}
			}
			startRow = last
		}
	}

	private fun applyFilterFieldOutline(outlineType: String) {
		UiUtils.uiRun { mainWindow.getTreeFilterField().putClientProperty("JComponent.outline", outlineType) }
	}

	private fun collectFilteredPaths() {
		UiUtils.notUiThreadGuard()
		filteredTreeNodes.clear()
		if (filter.isEmpty()) {
			return
		}
		val rootNode = getRoot()
		if (rootNode !is JRoot) {
			// 根节点为空或类型不符
			return
		}
		var nodesCount = 0
		var filteredCount = 0
		val en: Enumeration<TreeNode> = rootNode.depthFirstEnumeration()
		while (en.hasMoreElements()) {
			val node = en.nextElement()
			nodesCount++
			if (matchesFilter(node)) {
				addPathNodes(node)
				filteredCount++
			}
		}
		if (LOG.isDebugEnabled) {
			LOG.debug("Total nodes: {}, filtered: {}", nodesCount, filteredCount)
		}
		if (filteredTreeNodes.isEmpty()) {
			applyFilterFieldOutline("error")
		}
	}

	private fun addPathNodes(node: TreeNode) {
		if (!filteredTreeNodes.add(node)) {
			return
		}
		var parent = node.getParent()
		while (parent != null) {
			if (!filteredTreeNodes.add(parent)) {
				break
			}
			parent = parent.getParent()
		}
	}

	/**
	 * 判断给定节点是否匹配当前过滤条件。
	 *
	 * @param node 待判断节点
	 * @return 匹配则返回 true，节点应显示在树中。
	 */
	private fun matchesFilter(node: Any): Boolean {
		if (node is TextNode || node is JMethod) {
			return false
		}
		if (node is JResource) {
			if (node.getType() == JResource.JResType.FILE && node.getResFile()?.getType() == ResourceType.ARSC) {
				loadInnerResources(node)
			}
		}
		if (node is JNode) {
			val name = node.makeString()
			return name.lowercase().contains(filter.lowercase())
		}
		return false
	}

	private fun loadInnerResources(res: JResource) {
		// 加载 resource.arsc 的内部资源
		val loadTask: SimpleTask = res.getLoadTask() ?: return
		try {
			// 在后台线程阻塞等待该任务完成（setFilter 保证不在 EDT 调用）
			runBlocking { mainWindow.getBackgroundExecutor().executeAsync(loadTask).await() }
		} catch (e: Exception) {
			LOG.warn("Failed to load resource", e)
		}
	}

	/** 缓存上一次 [getChildCount] 的父节点及其过滤后的子列表。 */
	private class CacheNode {
		var parent: TreeNode? = null
		val children: MutableList<TreeNode> = ArrayList()

		fun clear() {
			parent = null
			children.clear()
		}
	}

	override fun getChild(parent: Any, index: Int): Any {
		if (cacheNode.parent === parent) {
			return cacheNode.children[index]
		}
		if (filter.isEmpty() || parent is JClass) {
			// 允许展开并查看类的全部方法
			return super.getChild(parent, index)
		}
		var i = 0
		val en = (parent as TreeNode).children()
		while (en.hasMoreElements()) {
			val child = en.nextElement()
			if (filteredTreeNodes.contains(child)) {
				if (i == index) {
					return child
				}
				i++
			}
		}
		throw IllegalArgumentException("No child at index $index")
	}

	override fun getChildCount(parent: Any): Int {
		if (filter.isEmpty()) {
			return super.getChildCount(parent)
		}
		val parentNode = parent as TreeNode
		if (!filteredTreeNodes.contains(parentNode)) {
			return 0
		}
		cacheNode.parent = parentNode
		val children = cacheNode.children
		children.clear()

		var count = 0
		val en = parentNode.children()
		while (en.hasMoreElements()) {
			val child = en.nextElement()
			if (filteredTreeNodes.contains(child)) {
				children.add(child)
				count++
			}
		}
		return count
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(FilterableTreeModel::class.java)
	}
}
