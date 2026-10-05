package kadx.gui.search.providers

import kadx.api.ResourceFile
import kadx.api.ResourceType
import kadx.api.plugins.utils.CommonFileUtils
import kadx.api.resources.ResourceContentType
import kadx.api.utils.CodeUtils
import kadx.gui.jobs.Cancelable
import kadx.gui.search.ISearchProvider
import kadx.gui.search.SearchSettings
import kadx.gui.treemodel.JNode
import kadx.gui.treemodel.JResSearchNode
import kadx.gui.treemodel.JResource
import kadx.gui.treemodel.JRoot
import kadx.gui.ui.MainWindow
import kadx.gui.ui.dialog.SearchDialog
import kadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import java.util.Collections
import java.util.Deque

/**
 * 资源文件内容搜索提供者。
 *
 * **做什么**：以广度优先方式遍历资源树，对每个资源节点加载文本内容并查找搜索词，
 * 命中后返回 [JResSearchNode]。文本资源按行截取，二进制资源按命中位置附近截取片段。
 *
 * **为什么用 `Deque` 而不是递归**：资源树可能很深，用显式队列可随时响应取消，
 * 并复用 [JResource] 的加载缓存。
 *
 * **为什么不是 `data class`**：它持有遍历状态与统计计数，属于有状态迭代器。
 */
class ResourceSearchProvider(
	mw: MainWindow,
	private val searchSettings: SearchSettings,
	private val searchDialog: SearchDialog,
) : ISearchProvider {

	private val resourceFilter: ResourceFilter = searchSettings.getResourceFilter()
	private val sizeLimit: Int = searchSettings.getResSizeLimit() * 1024 * 1024

	/**
	 * 待处理资源队列。使用 UI 节点以复用其加载缓存。
	 */
	private val resQueue: Deque<JResource>

	private var pos = 0

	private var loadErrors = 0
	private var skipBySize = 0

	init {
		val activeResource = searchSettings.getActiveResource()
		resQueue = if (activeResource != null) {
			ArrayDeque(Collections.singleton(activeResource))
		} else {
			initResQueue(mw)
		}
	}

	override fun next(cancelable: Cancelable): JNode? {
		while (true) {
			if (cancelable.isCanceled) {
				return null
			}
			val resNode = getNextResFile(cancelable) ?: return null
			val newResult = search(resNode)
			if (newResult != null) {
				return newResult
			}
			pos = 0
			resQueue.removeLast()
			addChildren(resNode)
			if (resQueue.isEmpty()) {
				return null
			}
		}
	}
	private fun search(resNode: JResource): JNode? {
		val content: String
		try {
			content = resNode.getCodeInfo().codeStr
		} catch (e: Exception) {
			LOG.error("Failed to load resource node content", e)
			return null
		}
		val searchString = searchSettings.getSearchString()
		val newPos = searchSettings.getSearchMethod().find(content, searchString, pos)
		if (newPos == -1) {
			return null
		}
		if (resNode.getContentType() == ResourceContentType.CONTENT_TEXT) {
			val lineStart = 1 + CodeUtils.getNewLinePosBefore(content, newPos)
			val lineEnd = CodeUtils.getNewLinePosAfter(content, newPos)
			val end = if (lineEnd == -1) content.length else lineEnd
			val line = content.substring(lineStart, end)
			this.pos = end
			return JResSearchNode(resNode, line.trim(), newPos)
		} else {
			val start = (newPos - 30).coerceAtLeast(0)
			val end = (newPos + 50).coerceAtMost(content.length)
			val line = content.substring(start, end)
			this.pos = newPos + searchString.length + 1
			return JResSearchNode(resNode, line, newPos)
		}
	}

	private fun getNextResFile(cancelable: Cancelable): JResource? {
		while (true) {
			val node = resQueue.peekLast()
			if (node == null || cancelable.isCanceled) {
				return null
			}
			if (node.getType() == JResource.JResType.FILE) {
				if (shouldProcess(node) && loadResNode(node)) {
					return node
				}
				resQueue.removeLast()
			} else {
				// 目录节点：展开子节点
				resQueue.removeLast()
				loadResNode(node)
				addChildren(node)
			}
		}
	}
	private fun updateProgressInfo() {
		val sb = StringBuilder()
		if (loadErrors != 0) {
			sb.append("  ").append(NLS.str("search_dialog.resources_load_errors", loadErrors))
		}
		if (skipBySize != 0) {
			sb.append("  ").append(NLS.str("search_dialog.resources_skip_by_size", skipBySize))
		}
		if (sb.length != 0) {
			sb.append("  ").append(NLS.str("search_dialog.resources_check_logs"))
		}
		searchDialog.updateProgressLabel(sb.toString())
	}

	private fun loadResNode(node: JResource): Boolean {
		try {
			node.loadNode()
			return true
		} catch (e: Exception) {
			LOG.error("Error load resource node: {}", node, e)
			loadErrors++
			updateProgressInfo()
			return false
		}
	}

	private fun addChildren(resNode: JResource) {
		resQueue.addAll(resNode.getSubNodes())
	}

	private fun initResQueue(mw: MainWindow): Deque<JResource> {
		val jRoot: JRoot = mw.getTreeRoot()
		val deque = ArrayDeque<JResource>(jRoot.getChildCount())
		val children = jRoot.children()
		while (children.hasMoreElements()) {
			val node = children.nextElement()
			if (node is JResource) {
				deque.add(node)
			}
		}
		return deque
	}
	private fun shouldProcess(resNode: JResource): Boolean {
		if (checkNotNull(resNode.getResFile()).getType() == ResourceType.ARSC) {
			// 不检查生成的资源表大小，否则会连带跳过所有子文件
			return resourceFilter.isAnyFile ||
				resourceFilter.getContentTypes().contains(ResourceContentType.CONTENT_TEXT) ||
				resourceFilter.getExtSet().contains("xml")
		}
		if (!isAllowedFileType(resNode)) {
			return false
		}
		return isAllowedFileSize(resNode)
	}

	private fun isAllowedFileType(resNode: JResource): Boolean {
		val resFile: ResourceFile = checkNotNull(resNode.getResFile())
		if (resourceFilter.isAnyFile) {
			return true
		}
		val resContentType = resNode.getContentType()
		if (resourceFilter.getContentTypes().contains(resContentType)) {
			return true
		}
		val fileExt = CommonFileUtils.getFileExtension(resFile.getOriginalName())
		if (fileExt != null && resourceFilter.getExtSet().contains(fileExt)) {
			return true
		}
		if (resContentType == ResourceContentType.CONTENT_UNKNOWN &&
			resourceFilter.getContentTypes().contains(ResourceContentType.CONTENT_BINARY)
		) {
			// 未知类型按二进制处理
			return true
		}
		return false
	}

	private fun isAllowedFileSize(resNode: JResource): Boolean {
		if (sizeLimit <= 0) {
			return true
		}
		try {
			val charsCount = resNode.getCodeInfo().codeStr.length
			val size = charsCount * 8L
			if (size > sizeLimit) {
				LOG.info(
					"Resource search skipped because of size limit. Resource '{}' size {} bytes, limit: {}",
					resNode.getName(),
					size,
					sizeLimit,
				)
				skipBySize++
				updateProgressInfo()
				return false
			}
			return true
		} catch (e: Exception) {
			LOG.warn("Resource load error: {}", resNode, e)
			loadErrors++
			updateProgressInfo()
			return false
		}
	}
	override fun progress(): Int = 0

	override fun total(): Int = 0

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ResourceSearchProvider::class.java)
	}
}
