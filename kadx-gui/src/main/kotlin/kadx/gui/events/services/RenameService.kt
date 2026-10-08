package kadx.gui.events.services

import kadx.api.JavaNode
import kadx.api.KadxDecompiler
import kadx.api.data.ICodeRename
import kadx.api.data.impl.KadxCodeData
import kadx.api.plugins.events.KadxEvents
import kadx.api.plugins.events.types.NodeRenamedByUser
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.jobs.TaskStatus
import kadx.gui.settings.KadxProject
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JRenameNode
import kadx.gui.ui.MainWindow
import kadx.gui.ui.codearea.ClassCodeContentPanel
import kadx.gui.ui.codearea.CodeArea
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.tab.TabbedPane
import kadx.gui.utils.CacheObject
import kadx.gui.utils.JNodeCache
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.function.Consumer

/**
 * 重命名服务：监听用户的“重命名节点”事件并完成后续联动更新。
 *
 * **处理流程**（每个事件）：
 * 1. 把重命名记录写入/更新到项目代码数据（[KadxCodeData]）；
 * 2. 后台线程重新加载受影响的类代码并刷新缓存；
 * 3. 回到 UI 线程刷新已打开的标签页与类树。
 *
 * **线程模型**：[kadx.gui.jobs.BackgroundExecutor]（协程调度）负责后台加载，
 * [UiUtils.uiRunAndWait] 回到 EDT 执行 UI 更新。
 */
class RenameService private constructor(private val mainWindow: MainWindow) {

	private fun process(event: NodeRenamedByUser) {
		try {
			LOG.debug("Applying rename event: {}", event)
			val timeStarted = System.nanoTime()
			val node = getRenameNode(event)
			updateCodeRenames { set -> processRename(node, event, set) }
			refreshState(node, timeStarted)
		} catch (e: Exception) {
			LOG.error("Rename failed", e)
			UiUtils.errorMessage(mainWindow, "Rename failed:\n" + Utils.getStackTrace(e))
		}
	}

	/** 从事件中解析出可重命名的树节点；无法解析时抛出运行时异常。 */
	private fun getRenameNode(event: NodeRenamedByUser): JRenameNode {
		val renameNode = event.getRenameNode()
		if (renameNode is JRenameNode) {
			return renameNode
		}
		val decompiler: KadxDecompiler = mainWindow.getWrapper().getDecompiler()
		val javaNode = decompiler.getJavaNodeByRef(event.getNode())
		if (javaNode != null) {
			val node = mainWindow.getCacheObject().nodeCache.makeFrom(javaNode)
			if (node is JRenameNode) {
				return node
			}
		}
		throw KadxRuntimeException("Failed to resolve node: " + event.getNode())
	}

	/** 根据事件更新重命名集合：重置名称或新名为空时移除别名，否则加入新记录。 */
	private fun processRename(node: JRenameNode, event: NodeRenamedByUser, renames: MutableSet<ICodeRename>) {
		val rename = node.buildCodeRename(event.getNewName(), renames)
		renames.remove(rename)
		if (event.isResetName() || event.getNewName().isEmpty()) {
			node.removeAlias()
		} else {
			renames.add(rename)
		}
	}

	/** 把重命名集合写回项目代码数据（排序后持久化）。 */
	private fun updateCodeRenames(updater: (MutableSet<ICodeRename>) -> Unit) {
		val project: KadxProject = mainWindow.getProject()
		val codeData: KadxCodeData = project.codeData
		val set = HashSet(codeData.getRenames())
		updater(set)
		val list = ArrayList(set)
		Collections.sort(list)
		codeData.setRenames(list)
		project.setCodeData(codeData)
	}

	/**
	 * 收集需要更新的顶层类，并在后台重新加载代码，最后在 EDT 上刷新标签页与类树。
	 * 小批量（< 10 个）直接 reload，大批量则 unload 以释放内存。
	 */
	private fun refreshState(node: JRenameNode, timeStarted: Long) {
		val toUpdate = ArrayList<JavaNode>()
		node.addUpdateNodes(toUpdate)

		val nodeCache: JNodeCache = mainWindow.getCacheObject().nodeCache
		val updatedTopClasses: MutableSet<JClass> = toUpdate
			.mapNotNull { nodeCache.makeFrom(it.getTopParentClass()) }
			.toMutableSet()

		LOG.debug("Classes to update: {}", updatedTopClasses)
		if (updatedTopClasses.isEmpty()) {
			return
		}
		mainWindow.getBackgroundExecutor().execute(
			"Refreshing",
			Runnable {
				mainWindow.getWrapper().reloadCodeData()
				// 在后台线程重新加载所有受影响类的代码（不使用 codeArea.backgroundRefreshClass，
				// 以免再起一个后台任务）。
				for (tab in mainWindow.getTabbedPane().tabs) {
					val rootClass = tab.getNode().getRootClass()
					if (rootClass != null && updatedTopClasses.contains(rootClass)) {
						rootClass.reload(mainWindow.getCacheObject())
					}
				}
				UiUtils.uiRunAndWait { refreshTabs(mainWindow.getTabbedPane(), updatedTopClasses) }
				refreshClasses(updatedTopClasses)
				LOG.debug("Finished rename, took " + (System.nanoTime() - timeStarted) + " ns")
			},
			{ status ->
				if (status == TaskStatus.CANCEL_BY_MEMORY) {
					mainWindow.showHeapUsageBar()
					UiUtils.errorMessage(mainWindow, NLS.str("message.memoryLow"))
				}
				node.reload(mainWindow)
			},
		)
	}

	/** 重新加载（小批量）或卸载（大批量）受影响类的缓存。 */
	private fun refreshClasses(updatedTopClasses: Set<JClass>) {
		val cache: CacheObject = mainWindow.getCacheObject()
		if (updatedTopClasses.size < 10) {
			// 小批量 => 重新加载
			LOG.debug("Classes to reload: {}", updatedTopClasses.size)
			for (cls in updatedTopClasses) {
				try {
					cls.reload(cache)
				} catch (e: Exception) {
					LOG.error("Failed to reload class: {}", cls.fullName, e)
				}
			}
		} else {
			// 大批量 => 卸载以释放内存
			LOG.debug("Classes to unload: {}", updatedTopClasses.size)
			for (cls in updatedTopClasses) {
				try {
					cls.unload(cache)
				} catch (e: Exception) {
					LOG.error("Failed to unload class: {}", cls.fullName, e)
				}
			}
		}
	}

	/** 在 EDT 上刷新受影响的已打开标签页（仅类代码面板）。 */
	private fun refreshTabs(tabbedPane: TabbedPane, updatedClasses: MutableSet<JClass>) {
		for (tab in tabbedPane.tabs) {
			val rootClass = tab.getNode().getRootClass()
			if (rootClass != null && updatedClasses.remove(rootClass)) {
				val contentPanel = tab as ClassCodeContentPanel
				val codeArea = contentPanel.getJavaCodePanel().getCodeArea() as CodeArea
				codeArea.refreshClass(true)
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(RenameService::class.java)

		/** 在全局事件总线上注册重命名监听器。 */
		fun init(mainWindow: MainWindow) {
			val renameService = RenameService(mainWindow)
			mainWindow.events().global()
				.addListener(KadxEvents.NODE_RENAMED_BY_USER, Consumer { event -> renameService.process(event) })
		}
	}
}
