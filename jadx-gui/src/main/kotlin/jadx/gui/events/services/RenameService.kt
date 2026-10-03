package jadx.gui.events.services

import jadx.api.JadxDecompiler
import jadx.api.JavaNode
import jadx.api.data.ICodeRename
import jadx.api.data.impl.JadxCodeData
import jadx.api.plugins.events.JadxEvents
import jadx.api.plugins.events.types.NodeRenamedByUser
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.jobs.TaskStatus
import jadx.gui.settings.JadxProject
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JRenameNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.ClassCodeContentPanel
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.CacheObject
import jadx.gui.utils.JNodeCache
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * 重命名服务：监听用户的“重命名节点”事件并完成后续联动更新。
 *
 * **处理流程**（每个事件）：
 * 1. 把重命名记录写入/更新到项目代码数据（[JadxCodeData]）；
 * 2. 后台线程重新加载受影响的类代码并刷新缓存；
 * 3. 回到 UI 线程刷新已打开的标签页与类树。
 *
 * **线程模型（阶段 5.1 保持不变）**：仍使用 [jadx.gui.jobs.BackgroundExecutor] +
 * [UiUtils.uiRunAndWait] 在 EDT 上执行 UI 更新，未引入协程。
 */
class RenameService private constructor(private val mainWindow: MainWindow) {

	private fun process(event: NodeRenamedByUser) {
		try {
			LOG.debug("Applying rename event: {}", event)
			val timeStarted = System.nanoTime()
			val node = getRenameNode(event)
			updateCodeRenames(Consumer { set -> processRename(node, event, set) })
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
		val decompiler: JadxDecompiler = mainWindow.getWrapper().getDecompiler()
		val javaNode = decompiler.getJavaNodeByRef(event.getNode())
		if (javaNode != null) {
			val node = mainWindow.getCacheObject().getNodeCache().makeFrom(javaNode)
			if (node is JRenameNode) {
				return node
			}
		}
		throw JadxRuntimeException("Failed to resolve node: " + event.getNode())
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
	private fun updateCodeRenames(updater: Consumer<MutableSet<ICodeRename>>) {
		val project: JadxProject = mainWindow.getProject()
		val codeData: JadxCodeData = project.getCodeData()
		val set = HashSet(codeData.getRenames())
		updater.accept(set)
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

		val nodeCache: JNodeCache = mainWindow.getCacheObject().getNodeCache()
		val updatedTopClasses: MutableSet<JClass> = toUpdate
			.stream()
			.map { it.getTopParentClass() }
			.map { nodeCache.makeFrom(it) }
			.filter { it != null }
			.map { checkNotNull(it) }
			.collect(Collectors.toSet())

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
				for (tab in mainWindow.getTabbedPane().getTabs()) {
					val rootClass = tab.getNode().getRootClass()
					if (rootClass != null && updatedTopClasses.contains(rootClass)) {
						rootClass.reload(mainWindow.getCacheObject())
					}
				}
				UiUtils.uiRunAndWait { refreshTabs(mainWindow.getTabbedPane(), updatedTopClasses) }
				refreshClasses(updatedTopClasses)
				LOG.debug("Finished rename, took " + (System.nanoTime() - timeStarted) + " ns")
			},
			Consumer { status ->
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
					LOG.error("Failed to reload class: {}", cls.getFullName(), e)
				}
			}
		} else {
			// 大批量 => 卸载以释放内存
			LOG.debug("Classes to unload: {}", updatedTopClasses.size)
			for (cls in updatedTopClasses) {
				try {
					cls.unload(cache)
				} catch (e: Exception) {
					LOG.error("Failed to unload class: {}", cls.getFullName(), e)
				}
			}
		}
	}

	/** 在 EDT 上刷新受影响的已打开标签页（仅类代码面板）。 */
	private fun refreshTabs(tabbedPane: TabbedPane, updatedClasses: MutableSet<JClass>) {
		for (tab in tabbedPane.getTabs()) {
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
		@JvmStatic
		fun init(mainWindow: MainWindow) {
			val renameService = RenameService(mainWindow)
			mainWindow.events().global()
				.addListener(JadxEvents.NODE_RENAMED_BY_USER, Consumer { event -> renameService.process(event) })
		}
	}
}
