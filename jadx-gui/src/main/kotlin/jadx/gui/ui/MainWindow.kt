package jadx.gui.ui

import ch.qos.logback.classic.Level
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatInspector
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.util.UIScale
import jadx.api.JadxArgs
import jadx.api.JavaClass
import jadx.api.JavaNode
import jadx.api.ResourceFile
import jadx.api.plugins.events.JadxEvents
import jadx.api.plugins.events.types.ReloadProject
import jadx.api.plugins.events.types.ReloadSettingsWindow
import jadx.api.plugins.utils.CommonFileUtils
import jadx.commons.app.JadxSystemInfo
import jadx.core.Jadx
import jadx.core.dex.nodes.ClassNode
import jadx.core.utils.ListUtils
import jadx.core.utils.StringUtils
import jadx.core.utils.android.AndroidManifestParser
import jadx.core.utils.android.AppAttribute
import jadx.core.utils.android.ApplicationParams
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.gui.JadxWrapper
import jadx.gui.cache.manager.CacheManager
import jadx.gui.device.debugger.BreakpointManager
import jadx.gui.events.services.RenameService
import jadx.gui.events.types.JadxGuiEventsImpl
import jadx.gui.jobs.BackgroundExecutor
import jadx.gui.jobs.DecompileTask
import jadx.gui.jobs.ExportTask
import jadx.gui.jobs.IBackgroundTask
import jadx.gui.jobs.TaskStatus
import jadx.gui.jobs.TaskWithExtraOnFinish
import jadx.gui.logs.LogCollector
import jadx.gui.logs.LogOptions
import jadx.gui.logs.LogPanel
import jadx.gui.plugins.context.CommonGuiPluginsContext
import jadx.gui.plugins.context.TreePopupMenuEntry
import jadx.gui.plugins.mappings.RenameMappingsGui
import jadx.gui.plugins.quark.QuarkDialog
import jadx.gui.report.ExceptionDialog
import jadx.gui.report.JadxExceptionHandler
import jadx.gui.settings.JadxProject
import jadx.gui.settings.JadxSettings
import jadx.gui.settings.JadxUpdateChannel
import jadx.gui.settings.data.SaveOptionEnum
import jadx.gui.settings.ui.JadxSettingsWindow
import jadx.gui.tree.TreeExpansionService
import jadx.gui.treemodel.ApkSignatureNode
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JLoadableNode
import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JPackage
import jadx.gui.treemodel.JResource
import jadx.gui.treemodel.JRoot
import jadx.gui.ui.action.ActionModel
import jadx.gui.ui.action.JadxGuiAction
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.codearea.AbstractCodeContentPanel
import jadx.gui.ui.codearea.EditorViewState
import jadx.gui.ui.codearea.theme.EditorThemeManager
import jadx.gui.ui.dialog.ADBDialog
import jadx.gui.ui.dialog.AboutDialog
import jadx.gui.ui.dialog.CharsetDialog
import jadx.gui.ui.dialog.GotoAddressDialog
import jadx.gui.ui.dialog.LogViewerDialog
import jadx.gui.ui.dialog.SearchDialog
import jadx.gui.ui.export.ExportProjectDialog
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.ui.hexviewer.HexInspectorPanel
import jadx.gui.ui.hexviewer.HexPreviewPanel
import jadx.gui.ui.menu.HiddenMenuItem
import jadx.gui.ui.menu.JadxMenu
import jadx.gui.ui.menu.JadxMenuBar
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.panel.IssuesPanel
import jadx.gui.ui.panel.JDebuggerPanel
import jadx.gui.ui.panel.ProgressPanel
import jadx.gui.ui.popupmenu.RecentProjectsMenuListener
import jadx.gui.ui.startpage.StartPageNode
import jadx.gui.ui.tab.EditorSyncManager
import jadx.gui.ui.tab.NavigationController
import jadx.gui.ui.tab.QuickTabsTree
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.ui.tab.TabsController
import jadx.gui.ui.tab.dnd.TabDndController
import jadx.gui.ui.treenodes.SummaryNode
import jadx.gui.ui.treenodes.UndisplayedStringsNode
import jadx.gui.update.IUpdateCallback
import jadx.gui.update.JadxUpdate
import jadx.gui.update.Release
import jadx.gui.utils.CacheObject
import jadx.gui.utils.DesktopEntryUtils
import jadx.gui.utils.FontUtils
import jadx.gui.utils.ILoadListener
import jadx.gui.utils.Icons
import jadx.gui.utils.LafManager
import jadx.gui.utils.Link
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.dbg.UIWatchDog
import jadx.gui.utils.fileswatcher.LiveReloadWorker
import jadx.gui.utils.shortcut.ShortcutsController
import jadx.gui.utils.ui.ActionHandler
import jadx.gui.utils.ui.FileOpenerHelper
import jadx.gui.utils.ui.NodeLabel
import org.exbin.bined.swing.section.SectCodeArea
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.DisplayMode
import java.awt.Font
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.AffineTransform
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.function.Consumer
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * jadx-gui 主窗口（应用唯一的顶层 `JFrame`）。
 *
 * **做什么**：负责组装菜单/工具栏、左侧包树、右侧标签页与底部日志/调试面板，
 * 并协调后台任务（[BackgroundExecutor]）、事件总线（[JadxGuiEventsImpl]）与各控制器。
 *
 * **线程模型**：本阶段（N1）已把后台任务迁移到协程 —— 所有 UI 操作仍在
 * EDT 上执行（`SwingUtilities.invokeLater` / [UiUtils.uiRun]），后台任务经
 * [BackgroundExecutor] 的协程作用域调度，耗时工作下放到 `Dispatchers.IO`。
 *
 * **为什么公共 getter 都写成显式函数**：该类是被几乎所有 gui 类引用的中心节点，
 * 保留 `fun getXxx()` 形式可让 Kotlin 调用点的 `.getXxx()` 写法零改动。
 */
class MainWindow(@Transient private val settings: JadxSettings) : JFrame() {

	/** 当前工程（可被重新加载/新建替换，故为可变属性）。 */
	@Transient
	private var project: JadxProject

	/** 反编译器封装。 */
	@Transient
	private val wrapper: JadxWrapper

	/** GUI 缓存（节点缓存、上次搜索词等）。 */
	@Transient
	private val cacheObject: CacheObject

	/** 磁盘缓存管理器。 */
	@Transient
	private val cacheManager: CacheManager

	/** 后台任务执行器（加载、反编译、导出等）。 */
	@Transient
	private val backgroundExecutor: BackgroundExecutor

	/** 全局事件总线实现。 */
	@Transient
	private val events: JadxGuiEventsImpl = JadxGuiEventsImpl()

	/** 树展开状态保存/恢复服务。 */
	@Transient
	private val treeExpansionService: TreeExpansionService

	private val tabsController: TabsController
	private val navController: NavigationController
	private val editorSyncManager: EditorSyncManager
	private val editorThemeManager: EditorThemeManager

	private lateinit var newProjectAction: JadxGuiAction
	private lateinit var saveProjectAction: JadxGuiAction

	private lateinit var mainPanel: JPanel
	private lateinit var treeSplitPane: JSplitPane
	private lateinit var rightSplitPane: JSplitPane
	private lateinit var bottomSplitPane: JSplitPane
	private lateinit var quickTabsAndCodeSplitPane: JSplitPane

	private lateinit var tree: JTree
	private lateinit var treeModel: DefaultTreeModel
	private var treeRoot: JRoot? = null
	private lateinit var tabbedPane: TabbedPane
	private lateinit var heapUsageBar: HeapUsageBar
	private var treeReloading: Boolean = false

	private var isFlattenPackage: Boolean = false
	private lateinit var flatPkgButton: JToggleButton
	private lateinit var flatPkgMenuItem: JCheckBoxMenuItem

	private lateinit var deobfToggleBtn: JToggleButton
	private lateinit var deobfMenuItem: JCheckBoxMenuItem

	private lateinit var liveReloadMenuItem: JCheckBoxMenuItem
	private val liveReloadWorker: LiveReloadWorker

	@Transient
	private lateinit var updateLink: Link

	@Transient
	private lateinit var progressPane: ProgressPanel

	@Transient
	private lateinit var issuesPanel: IssuesPanel

	@Transient
	private var logPanel: LogPanel? = null

	@Transient
	private var debuggerPanel: JDebuggerPanel? = null

	@Transient
	private var quickTabsTree: QuickTabsTree? = null

	private val loadListeners: MutableList<ILoadListener> = ArrayList()
	private val treeUpdateListener: MutableList<(JRoot) -> Unit> = ArrayList()
	private var loaded: Boolean = false
	private var settingsOpen: Boolean = false
	private var showUndisplayedCharsDialog: Boolean = false

	private val shortcutsController: ShortcutsController
	private lateinit var menuBar: JadxMenuBar
	private lateinit var pluginsMenu: JMenu

	/** 十六进制查看器菜单；由 [initHexViewMenu] 填充。 */
	lateinit var hexViewerMenu: JMenu

	private val renameMappings: RenameMappingsGui

	init {
		project = JadxProject(this)
		wrapper = JadxWrapper(this)
		cacheObject = CacheObject(wrapper)
		liveReloadWorker = LiveReloadWorker(this)
		renameMappings = RenameMappingsGui(this)
		cacheManager = CacheManager(settings)
		shortcutsController = ShortcutsController(settings)
		tabsController = TabsController(this)
		navController = NavigationController(this)
		editorThemeManager = EditorThemeManager(settings)

		JadxEventQueue.register()
		JadxExceptionHandler.register(this)
		resetCache()
		initUI()
		editorSyncManager = EditorSyncManager(this, tabbedPane)
		backgroundExecutor = BackgroundExecutor(settings, progressPane)
		treeExpansionService = TreeExpansionService(this, tree)
		initMenuAndToolbar()
		UiUtils.setWindowIcons(this)
		shortcutsController.registerMouseEventListener(this)
		loadSettings()
		initEvents()

		update()
		checkForUpdate()
	}

	fun init() {
		pack()
		setLocationAndPosition()
		treeSplitPane.setDividerLocation(settings.getTreeWidth())
		heapUsageBar.setVisible(settings.isShowHeapUsageBar())
		setVisible(true)
		processCommandLineArgs()
	}

	private fun processCommandLineArgs() {
		if (settings.getFiles().isEmpty()) {
			tabsController.selectTab(StartPageNode())
		} else {
			open(FileUtils.fileNamesToPaths(settings.getFiles()), Runnable { handleSelectClassOption() })
		}
	}

	/** 处理 `--select-class` 命令行参数：按别名或原始类名跳转到指定类。 */
	private fun handleSelectClassOption() {
		val cmdSelectClass = settings.getCmdSelectClass()
		if (cmdSelectClass != null) {
			var javaNode: JavaNode? = wrapper.searchJavaClassByFullAlias(cmdSelectClass)
			if (javaNode == null) {
				javaNode = wrapper.searchJavaClassByOrigClassName(cmdSelectClass)
			}
			if (javaNode == null) {
				JOptionPane.showMessageDialog(
					this,
					NLS.str("msg.cmd_select_class_error", cmdSelectClass),
					NLS.str("error_dialog.title"),
					JOptionPane.ERROR_MESSAGE,
				)
				return
			}
			tabsController.codeJump(checkNotNull(cacheObject.getNodeCache().makeFrom(javaNode)))
		}
	}

	/** 启动时在后台检查更新，回调在 EDT 上更新标题栏右侧的更新链接。 */
	private fun checkForUpdate() {
		if (!settings.isCheckForUpdates()) {
			return
		}
		JadxUpdate().check(
			settings.getJadxUpdateChannel(),
			object : IUpdateCallback {
				override fun onUpdate(r: Release) {
					SwingUtilities.invokeLater(
						Runnable {
							when (settings.getJadxUpdateChannel()) {
								JadxUpdateChannel.STABLE -> updateLink.setUrl(JadxUpdate.JADX_RELEASES_URL)
								JadxUpdateChannel.UNSTABLE -> updateLink.setUrl(JadxUpdate.JADX_ARTIFACTS_URL)
							}
							updateLink.setText(NLS.str("menu.update_label", r.name))
							updateLink.setVisible(true)
						},
					)
				}
			},
		)
	}

	fun openFileDialog() {
		showOpenDialog(FileOpenMode.OPEN)
	}

	fun openProjectDialog() {
		showOpenDialog(FileOpenMode.OPEN_PROJECT)
	}

	private fun showOpenDialog(mode: FileOpenMode) {
		saveAll()
		if (!ensureProjectIsSaved()) {
			return
		}
		val fileDialog = FileDialogWrapper(this, mode)
		val openPaths = fileDialog.show()
		if (openPaths.isNotEmpty()) {
			settings.setLastOpenFilePath(checkNotNull(fileDialog.getCurrentDir()))
			open(openPaths)
		}
	}

	fun addFiles() {
		val fileDialog = FileDialogWrapper(this, FileOpenMode.ADD)
		val addPaths = fileDialog.show()
		if (addPaths.isNotEmpty()) {
			addFiles(addPaths)
		}
	}

	fun addFiles(addPaths: List<Path>) {
		project.setFilePaths(ListUtils.distinctMergeSortedLists(addPaths, project.getFilePaths()))
		reopen()
	}

	private fun newProject() {
		saveAll()
		if (!ensureProjectIsSaved()) {
			return
		}
		UiUtils.bgRun(
			Runnable {
				closeAll()
				updateProject(JadxProject(this))
			},
		)
	}

	private fun saveProject() {
		saveOpenTabs()
		if (!project.isSaveFileSelected()) {
			saveProjectAs()
		} else {
			project.save()
			update()
		}
	}

	private fun saveProjectAs() {
		val fileDialog = FileDialogWrapper(this, FileOpenMode.SAVE_PROJECT)
		if (project.getFilePaths().size == 1) {
			// 只加载了一个文件时，建议把工程文件保存在该文件旁边
			val projectPath = getProjectPathForFile(project.getFilePaths()[0])
			fileDialog.setSelectedFile(projectPath)
		}
		val saveFiles = fileDialog.show()
		if (saveFiles.isEmpty()) {
			return
		}
		settings.setLastSaveProjectPath(checkNotNull(fileDialog.getCurrentDir()))
		var savePath = saveFiles[0]
		if (!savePath.getFileName().toString().lowercase(Locale.ROOT).endsWith(JadxProject.PROJECT_EXTENSION)) {
			savePath = savePath.resolveSibling(savePath.getFileName().toString() + "." + JadxProject.PROJECT_EXTENSION)
		}
		if (Files.exists(savePath)) {
			val res = JOptionPane.showConfirmDialog(
				this,
				NLS.str("confirm.save_as_message", savePath.getFileName()),
				NLS.str("confirm.save_as_title"),
				JOptionPane.YES_NO_OPTION,
			)
			if (res == JOptionPane.NO_OPTION) {
				return
			}
		}
		project.saveAs(savePath)
		settings.addRecentProject(savePath)
		update()
	}

	fun removeInput(file: Path) {
		val dialogResult = JOptionPane.showConfirmDialog(
			this,
			NLS.str("message.confirm_remove_script"),
			NLS.str("msg.warning_title"),
			JOptionPane.YES_NO_OPTION,
		)
		if (dialogResult == JOptionPane.NO_OPTION) {
			return
		}

		// 原 Java 直接修改内部列表，这里保持同一语义（列表实例为 ArrayList）
		@Suppress("UNCHECKED_CAST")
		val inputs = project.getFilePaths() as MutableList<Path>
		inputs.remove(file)
		refreshTree(inputs)
	}

	fun renameInput(file: Path) {
		val newName = JOptionPane.showInputDialog(this, NLS.str("message.enter_new_name"), file.getFileName().toString())
		if (newName == null || newName.trim().isEmpty()) {
			return
		}
		val targetPath = file.resolveSibling(newName)

		val success = FileUtils.renameFile(file, targetPath)
		if (success) {
			@Suppress("UNCHECKED_CAST")
			val inputs = project.getFilePaths() as MutableList<Path>
			inputs.remove(file)
			inputs.add(targetPath)

			refreshTree(inputs)
		} else {
			JOptionPane.showMessageDialog(
				this,
				NLS.str("message.could_not_rename"),
				NLS.str("message.errorTitle"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	private fun refreshTree(inputs: List<Path>) {
		project.setFilePaths(inputs)
		project.save()
		reopen()
	}

	fun open(path: Path) {
		open(listOf(path), UiUtils.EMPTY_RUNNABLE)
	}

	fun open(paths: List<Path>) {
		open(paths, UiUtils.EMPTY_RUNNABLE)
	}

	private fun open(paths: List<Path>, onFinish: Runnable) {
		saveAll()
		UiUtils.bgRun(
			Runnable {
				closeAll()
				if (paths.size == 1 && openSingleFile(paths[0], onFinish)) {
					return@Runnable
				}
				// 新建工程
				project = JadxProject(this)
				project.setFilePaths(paths)
				showUndisplayedCharsDialog = false
				loadFiles(onFinish)
			},
		)
	}

	private fun openSingleFile(singleFile: Path, onFinish: Runnable): Boolean {
		val fileName = singleFile.getFileName() ?: return false
		val fileExtension = CommonFileUtils.getFileExtension(fileName.toString())
		if (fileExtension != null && fileExtension.equals(JadxProject.PROJECT_EXTENSION, ignoreCase = true)) {
			openProject(singleFile, onFinish)
			return true
		}
		// 检查是否已用默认名保存过工程文件
		val projectPath = getProjectPathForFile(singleFile)
		if (Files.exists(projectPath)) {
			openProject(projectPath, onFinish)
			return true
		}
		return false
	}

	fun reopen() {
		LOG.debug("starting reopen")
		UiUtils.bgRun(
			Runnable {
				backgroundExecutor.waitForComplete()
				synchronized(ReloadProject.EVENT) {
					saveAll()
					closeAll()
					System.gc()
					loadFiles(
						Runnable {
							menuBar.reloadShortcuts()
							events().send(ReloadSettingsWindow.INSTANCE)
							LOG.debug("reopen complete")
						},
					)
				}
			},
		)
	}

	private fun openProject(path: Path, onFinish: Runnable) {
		LOG.debug("Loading project: {}", path)
		project = JadxProject.load(this, path)
		settings.addRecentProject(path)
		loadFiles(onFinish)
	}

	private fun loadFiles(onFinish: Runnable) {
		project.verifyFiles()
		if (project.getFilePaths().isEmpty()) {
			tabsController.selectTab(StartPageNode())
			onFinish.run()
			return
		}
		backgroundExecutor.execute(
			NLS.str("progress.load"),
			Runnable {
				try {
					wrapper.open()
				} catch (e: Exception) {
					LOG.error("Project load error", e)
					closeAll()
				}
			},
			{ status ->
				if (status == TaskStatus.CANCEL_BY_MEMORY) {
					showHeapUsageBar()
					UiUtils.errorMessage(this, NLS.str("message.memoryLow"))
					return@execute
				}
				if (status != TaskStatus.COMPLETE) {
					LOG.warn("Loading task incomplete, status: {}", status)
					return@execute
				}
				checkLoadedStatus()
				onOpen(onFinish)
			},
		)
	}

	private fun saveAll() {
		saveOpenTabs()
		project.setTreeExpansions(treeExpansionService.save())
		BreakpointManager.saveAndExit()
	}

	private fun closeAll() {
		UiUtils.notUiThreadGuard()
		cancelBackgroundJobs()
		UiUtils.uiRunAndWait(
			Runnable {
				tabsController.forceCloseAllTabs()
				tabbedPane.reset()
				navController.reset()
				shortcutsController.reset()
				clearTree()
				UiUtils.resetClipboardOwner()
				update()
			},
		)
		wrapper.close()
		LogCollector.getInstance().reset()
		resetCache()
		notifyLoadListeners(false)
	}

	private fun checkLoadedStatus() {
		if (wrapper.getClasses().isNotEmpty()) {
			return
		}
		val errors = issuesPanel.getErrorsCount()
		if (errors > 0) {
			val result = JOptionPane.showConfirmDialog(
				this,
				NLS.str("message.load_errors", errors),
				NLS.str("message.errorTitle"),
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.ERROR_MESSAGE,
			)
			if (result == JOptionPane.OK_OPTION) {
				showLogViewer(LogOptions.allWithLevel(Level.ERROR))
			}
		} else {
			showLogViewer(LogOptions.allWithLevel(Level.WARN))
			UiUtils.showMessageBox(this, NLS.str("message.no_classes"))
		}
	}

	private fun onOpen(onFinish: Runnable) {
		initTree()
		updateLiveReload(project.isEnableLiveReload())
		BreakpointManager.init(project.getFilePaths()[0].toAbsolutePath().getParent())
		val openTabs = project.getOpenTabs(this)
		backgroundExecutor.startLoading(
			Runnable { preLoadOpenTabs(openTabs) },
			Runnable {
				restoreOpenTabs(openTabs)
				update()
				notifyLoadListeners(true)
				onFinish.run()
				checkIfCodeHasNonPrintableChars()
				runInitialBackgroundJobs()
				prepareInitialView()
			},
		)
		// 在加载任务之后排队恢复树状态
		treeExpansionService.load(project.getTreeExpansions())
	}

	private fun prepareInitialView() {
		UiUtils.uiThreadGuard()
		// 只有一个类时直接打开
		wrapper.getCurrentDecompiler()?.let { decompiler ->
			val classes = decompiler.getClasses()
			if (classes.size == 1) {
				val singleCls = checkNotNull(cacheObject.getNodeCache().makeFrom(classes[0]))
				tabsController.codeJump(singleCls, true)
				selectNodeInTree(singleCls)
			}
		}
	}

	fun passesReloaded() {
		UiUtils.uiThreadGuard()
		tabbedPane.reloadInactiveTabs()
		reloadTreePreservingState()
	}

	private fun initEvents() {
		events().global().addListener(JadxEvents.RELOAD_PROJECT, Consumer { UiUtils.uiRun(Runnable { reopen() }) })
		RenameService.init(this)
	}

	fun updateLiveReload(state: Boolean) {
		if (liveReloadWorker.isStarted() == state) {
			return
		}
		project.setEnableLiveReload(state)
		liveReloadMenuItem.setEnabled(false)
		backgroundExecutor.execute(
			(if (state) "Starting" else "Stopping") + " live reload",
			Runnable { liveReloadWorker.updateState(state) },
			{
				liveReloadMenuItem.setState(state)
				liveReloadMenuItem.setEnabled(true)
			},
		)
	}

	private fun addTreeCustomNodes() {
		checkNotNull(treeRoot).replaceCustomNode(ApkSignatureNode.getApkSignature(wrapper))
		checkNotNull(treeRoot).replaceCustomNode(SummaryNode(this))
	}

	private fun ensureProjectIsSaved(): Boolean {
		if (project.isSaved() || project.isInitial()) {
			return true
		}
		if (project.getFilePaths().isEmpty()) {
			// 忽略空白工程的保存
			return true
		}
		// 检查是否已保存过“如何处理未保存工程”的设置
		if (settings.getSaveOption() == SaveOptionEnum.NEVER) {
			return true
		}
		if (settings.getSaveOption() == SaveOptionEnum.ALWAYS) {
			saveProject()
			return true
		}

		val remember = JCheckBox(NLS.str("confirm.remember"))
		val message = JLabel(NLS.str("confirm.not_saved_message"))

		val inner = JPanel(BorderLayout())
		inner.add(remember, BorderLayout.SOUTH)
		inner.add(message, BorderLayout.NORTH)

		val res = JOptionPane.showConfirmDialog(
			this,
			inner,
			NLS.str("confirm.not_saved_title"),
			JOptionPane.YES_NO_CANCEL_OPTION,
		)
		when (res) {
			JOptionPane.YES_OPTION -> {
				if (remember.isSelected()) {
					settings.setSaveOption(SaveOptionEnum.ALWAYS)
					settings.sync()
				}
				saveProject()
				return true
			}

			JOptionPane.NO_OPTION -> {
				if (remember.isSelected()) {
					settings.setSaveOption(SaveOptionEnum.NEVER)
					settings.sync()
				}
				return true
			}

			JOptionPane.CANCEL_OPTION -> return false
		}
		return true
	}

	fun updateProject(jadxProject: JadxProject) {
		project = jadxProject
		UiUtils.uiRun(Runnable { update() })
	}

	fun update() {
		UiUtils.uiThreadGuard()
		newProjectAction.setEnabled(!project.isInitial())
		saveProjectAction.setEnabled(loaded && !project.isSaved())
		deobfToggleBtn.setSelected(settings.isDeobfuscationOn())
		renameMappings.onUpdate(loaded)

		val projectPath = project.getProjectPath()
		val pathString = if (projectPath == null) {
			""
		} else {
			" [" + projectPath.toAbsolutePath().getParent() + ']'
		}
		setTitle(
			(if (project.isSaved()) "" else "*") +
				project.getName() + pathString + " - " + DEFAULT_TITLE,
		)
	}

	protected fun resetCache() {
		cacheObject.reset()
	}

	@Synchronized
	fun runInitialBackgroundJobs() {
		if (settings.isAutoStartJobs()) {
			Timer().schedule(
				object : TimerTask() {
					override fun run() {
						requestFullDecompilation()
					}
				},
				1000,
			)
		}
	}

	fun requestFullDecompilation() {
		if (cacheObject.isFullDecompilationFinished()) {
			return
		}
		backgroundExecutor.execute(DecompileTask(this))
	}

	fun resetCodeCache() {
		backgroundExecutor.execute(
			NLS.str("preferences.cache.task.delete"),
			Runnable {
				try {
					wrapper.getCurrentDecompiler()?.let { jadx ->
						try {
							jadx.getArgs().codeCache.close()
						} catch (e: Exception) {
							LOG.error("Failed to close code cache", e)
						}
					}
					val cacheDir = project.getCacheDir()
					project.resetCacheDir()
					FileUtils.deleteDirIfExists(cacheDir)
				} catch (e: Exception) {
					LOG.error("Error during code cache reset", e)
				}
			},
			{ events().send(ReloadProject.EVENT) },
		)
	}

	fun cancelBackgroundJobs() {
		backgroundExecutor.cancelAll()
	}

	fun exportProject() {
		val dialog = ExportProjectDialog(
			this,
			{ props ->
				val args = wrapper.getArgs()
				if (props.isAsGradleMode()) {
					args.exportGradleType = props.getExportGradleType()
					args.isSkipSources = false
					args.isSkipResources = false
				} else {
					args.exportGradleType = null
					args.isSkipSources = props.isSkipSources()
					args.isSkipResources = props.isSkipResources()
				}
				backgroundExecutor.execute(ExportTask(this, wrapper, File(checkNotNull(props.getExportPath()))))
			},
		)
		dialog.setVisible(true)
	}

	fun initTree() {
		val root = JRoot(this)
		treeRoot = root
		root.setFlatPackages(isFlattenPackage)
		treeModel.setRoot(root)
		addTreeCustomNodes()
		root.update()
		reloadTree()
	}

	private fun clearTree() {
		treeRoot = null
		treeModel.setRoot(null)
		treeModel.reload()
	}

	fun reloadTree() {
		treeReloading = true
		val root = checkNotNull(treeRoot)
		treeUpdateListener.forEach { listener -> listener(root) }
		treeModel.reload()
		treeReloading = false
	}

	fun rebuildPackagesTree() {
		checkNotNull(treeRoot).update()
	}

	/**
	 * 重命名后简单保存并恢复树状态。
	 * TODO: 也许需要改进为只查找并更新发生变化的节点
	 */
	fun reloadTreePreservingState() {
		val treePath = treeExpansionService.save()
		reloadTree()
		treeExpansionService.load(treePath)
	}

	private fun toggleFlattenPackage() {
		setFlattenPackage(!isFlattenPackage)
	}

	private fun setFlattenPackage(value: Boolean) {
		isFlattenPackage = value
		settings.setFlattenPackage(isFlattenPackage)

		flatPkgButton.setSelected(isFlattenPackage)
		flatPkgMenuItem.setState(isFlattenPackage)

		val root = treeModel.getRoot()
		if (root is JRoot) {
			root.setFlatPackages(isFlattenPackage)
			reloadTree()
		}
	}

	private fun toggleDeobfuscation() {
		val deobfOn = !settings.isDeobfuscationOn()
		settings.setDeobfuscationOn(deobfOn)
		settings.sync()

		deobfToggleBtn.setSelected(deobfOn)
		deobfMenuItem.setState(deobfOn)
		reopen()
	}

	private fun nodeClickAction(obj: Any?): Boolean {
		if (obj == null) {
			return false
		}
		try {
			if (obj is JResource) {
				val resFile = obj.getResFile()
				if (resFile != null) {
					if (JResource.isOpenInExternalTool(resFile.getType())) {
						FileOpenerHelper.openFile(this, obj)
						return true
					}
					if (JResource.isSupportedForView(resFile.getType())) {
						tabsController.selectTab(obj, true)
						return true
					}
				}
			} else if (obj is JNode) {
				if (obj.hasContent() || obj.getJParent() != null) {
					tabsController.codeJump(obj, true)
					return true
				}
			}
		} catch (e: Exception) {
			LOG.error("Content loading error", e)
		}
		return false
	}

	private fun treeRightClickAction(e: MouseEvent) {
		val node = getJNodeUnderMouse(e) ?: return
		var menu = node.onTreePopupMenu(this)
		val pluginsContext = wrapper.getGuiPluginsContext()
		for (entry in pluginsContext.getTreePopupMenuEntries()) {
			val menuItem = entry.buildEntry(node)
			if (menuItem != null) {
				if (menu == null) {
					menu = JPopupMenu()
				}
				menu.add(menuItem)
			}
		}
		if (menu != null) {
			menu.show(e.getComponent(), e.getX(), e.getY())
		}
	}

	private fun getJNodeUnderMouse(mouseEvent: MouseEvent): JNode? {
		val treeNode = UiUtils.getTreeNodeUnderMouse(tree, mouseEvent)
		if (treeNode is JNode) {
			return treeNode
		}
		return null
	}

	// TODO: 把树组件抽成单独的类
	fun selectNodeInTree(node: JNode) {
		var target = node
		if (target.getParent() == null && treeRoot != null) {
			// 节点尚未注册进树，需要先搜索
			val found = checkNotNull(treeRoot).searchNode(target)
			if (found == null) {
				LOG.error("Class not found in tree")
				return
			}
			target = found
		}
		val pathNodes = treeModel.getPathToRoot(target) ?: return
		val path = TreePath(pathNodes)
		tree.setSelectionPath(path)
		tree.makeVisible(path)
		tree.scrollPathToVisible(path)
		tree.requestFocus()
	}

	fun textSearch() {
		val panel = tabbedPane.getSelectedContentPanel()
		if (panel is AbstractCodeContentPanel) {
			val codeArea = panel.getCodeArea()
			if (codeArea != null) {
				var preferText = codeArea.getSelectedText()
				if (StringUtils.isEmpty(preferText)) {
					preferText = codeArea.getWordUnderCaret()
				}
				if (!StringUtils.isEmpty(preferText)) {
					SearchDialog.searchText(this, checkNotNull(preferText))
					return
				}
			}
		}
		SearchDialog.search(this, SearchDialog.SearchPreset.TEXT)
	}

	private fun sendActionsToHexViewer(action: ActionModel) {
		val hexPreviewPanel = getCurrentHexViewTab()
		if (hexPreviewPanel != null) {
			val inspector = hexPreviewPanel.getInspector()
			val hexEditor = hexPreviewPanel.getEditor()
			when (action) {
				ActionModel.HEX_VIEWER_SHOW_INSPECTOR -> hexPreviewPanel.getInspector().isVisible = !inspector.isVisible

				ActionModel.HEX_VIEWER_CHANGE_ENCODING -> {
					val result = CharsetDialog.chooseCharset(this, hexEditor.getCharset().name())
					if (!StringUtils.isEmpty(result)) {
						hexEditor.setCharset(Charset.forName(checkNotNull(result)))
					}
				}

				ActionModel.HEX_VIEWER_GO_TO_ADDRESS -> GotoAddressDialog().showSetSelectionDialog(hexEditor)

				ActionModel.HEX_VIEWER_FIND -> hexPreviewPanel.showSearchBar()

				else -> {
					// 其余 ActionModel 与本菜单无关
				}
			}
		}
	}

	fun getCurrentHexViewTab(): HexPreviewPanel? {
		val panel = tabbedPane.getSelectedContentPanel()
		if (panel is AbstractCodeContentPanel) {
			val childrenComponent = panel.getChildrenComponent()
			if (childrenComponent is HexPreviewPanel) {
				return childrenComponent
			}
		}
		return null
	}

	fun updateHexViewMenuEnabled() {
		hexViewerMenu.setEnabled(getCurrentHexViewTab() != null)
	}

	fun goToMainActivity() {
		val parser = AndroidManifestParser(
			AndroidManifestParser.getAndroidManifest(wrapper.getResources()),
			EnumSet.of(AppAttribute.MAIN_ACTIVITY),
			wrapper.getArgs().security,
		)
		if (!parser.isManifestFound()) {
			JOptionPane.showMessageDialog(
				this,
				NLS.str("error_dialog.not_found", "AndroidManifest.xml"),
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
			return
		}
		try {
			val results = parser.parse()
			if (results.mainActivity == null) {
				throw JadxRuntimeException("Failed to get main activity name from manifest")
			}
			val mainActivityClass = results.getMainActivityJavaClass(wrapper.getDecompiler())
			if (mainActivityClass == null) {
				throw JadxRuntimeException("Failed to find main activity class: " + results.mainActivity)
			}
			tabsController.codeJump(checkNotNull(cacheObject.getNodeCache().makeFrom(mainActivityClass)))
		} catch (e: Exception) {
			LOG.error("Main activity not found", e)
			JOptionPane.showMessageDialog(
				this,
				NLS.str("error_dialog.not_found", "Main Activity"),
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	fun goToApplication() {
		val parser = AndroidManifestParser(
			AndroidManifestParser.getAndroidManifest(wrapper.getResources()),
			EnumSet.of(AppAttribute.APPLICATION),
			wrapper.getArgs().security,
		)
		if (!parser.isManifestFound()) {
			JOptionPane.showMessageDialog(
				this,
				NLS.str("error_dialog.not_found", "AndroidManifest.xml"),
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
			return
		}
		try {
			val results = parser.parse()
			if (results.application == null) {
				throw JadxRuntimeException("Failed to get application from manifest")
			}
			val applicationClass = results.getApplicationJavaClass(wrapper.getDecompiler())
			if (applicationClass == null) {
				throw JadxRuntimeException("Failed to find application class: " + results.application)
			}
			tabsController.codeJump(checkNotNull(cacheObject.getNodeCache().makeFrom(applicationClass)))
		} catch (e: Exception) {
			LOG.error("Application not found", e)
			JOptionPane.showMessageDialog(
				this,
				NLS.str("error_dialog.not_found", "Application"),
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	fun goToAndroidManifest() {
		val androidManifest = AndroidManifestParser.getAndroidManifest(wrapper.getResources())
		if (androidManifest == null) {
			JOptionPane.showMessageDialog(
				this,
				NLS.str("error_dialog.not_found", "AndroidManifest.xml"),
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
			return
		}

		val res = JResource(androidManifest, androidManifest.getDeobfName(), JResource.JResType.FILE)
		tabsController.codeJump(res)
	}

	private fun initMenuAndToolbar() {
		val openAction = JadxGuiAction(ActionModel.OPEN, Runnable { openFileDialog() })
		val openProject = JadxGuiAction(ActionModel.OPEN_PROJECT, Runnable { openProjectDialog() })

		val addFilesAction = JadxGuiAction(ActionModel.ADD_FILES, Runnable { addFiles() })
		newProjectAction = JadxGuiAction(ActionModel.NEW_PROJECT, Runnable { newProject() })
		saveProjectAction = JadxGuiAction(ActionModel.SAVE_PROJECT, Runnable { saveProject() })
		val saveProjectAsAction = JadxGuiAction(ActionModel.SAVE_PROJECT_AS, Runnable { saveProjectAs() })
		val reloadAction = JadxGuiAction(ActionModel.RELOAD, Runnable { UiUtils.uiRun(Runnable { reopen() }) })
		val liveReloadAction = JadxGuiAction(
			ActionModel.LIVE_RELOAD,
			Runnable { updateLiveReload(!project.isEnableLiveReload()) },
		)

		liveReloadMenuItem = JCheckBoxMenuItem(liveReloadAction)
		liveReloadMenuItem.setState(project.isEnableLiveReload())

		val exportAction = JadxGuiAction(ActionModel.EXPORT, Runnable { exportProject() })

		val recentProjects = JadxMenu(NLS.str("menu.recent_projects"), shortcutsController)
		recentProjects.addMenuListener(RecentProjectsMenuListener(this, recentProjects))

		hexViewerMenu = JadxMenu(NLS.str("menu.hex_viewer"), shortcutsController)
		initHexViewMenu()

		val prefsAction = JadxGuiAction(ActionModel.PREFS, Runnable { openSettings() })
		val exitAction = JadxGuiAction(ActionModel.EXIT, Runnable { closeWindow() })

		isFlattenPackage = settings.isFlattenPackage()
		flatPkgMenuItem = JCheckBoxMenuItem(NLS.str("menu.flatten"), Icons.FLAT_PKG)
		flatPkgMenuItem.setState(isFlattenPackage)

		val enablePreviewTabAction = JadxGuiAction(
			ActionModel.PREVIEW_TAB,
			Runnable { settings.setEnablePreviewTab(!settings.isEnablePreviewTab()) },
		)
		enablePreviewTabAction.setSelected(settings.isEnablePreviewTab())

		val heapUsageBarMenuItem = JCheckBoxMenuItem(NLS.str("menu.heapUsageBar"))
		heapUsageBarMenuItem.setState(settings.isShowHeapUsageBar())
		heapUsageBarMenuItem.addActionListener {
			settings.setShowHeapUsageBar(!settings.isShowHeapUsageBar())
			heapUsageBar.setVisible(settings.isShowHeapUsageBar())
		}

		val alwaysSelectOpened = JCheckBoxMenuItem(NLS.str("menu.alwaysSelectOpened"))
		alwaysSelectOpened.setState(settings.isAlwaysSelectOpened())
		alwaysSelectOpened.addActionListener {
			settings.setAlwaysSelectOpened(!settings.isAlwaysSelectOpened())
			if (settings.isAlwaysSelectOpened()) {
				editorSyncManager.sync()
			}
		}

		val dockLog = JCheckBoxMenuItem(NLS.str("menu.dock_log"))
		dockLog.setState(settings.isDockLogViewer())
		dockLog.addActionListener { settings.saveDockLogViewer(!settings.isDockLogViewer()) }

		val quickTabsAction = ActionHandler(
			Runnable {
				val visible = quickTabsTree == null
				setQuickTabsVisibility(visible)
				settings.saveDockQuickTabs(visible)
			},
		)
		quickTabsAction.setNameAndDesc(NLS.str("menu.dock_quick_tabs"))
		quickTabsAction.setIcon(Icons.QUICK_TABS)
		quickTabsAction.setSelected(settings.isDockQuickTabs())
		setQuickTabsVisibility(settings.isDockQuickTabs())

		val syncAction = JadxGuiAction(ActionModel.SYNC, Runnable { editorSyncManager.sync() })
		val textSearchAction = JadxGuiAction(ActionModel.TEXT_SEARCH, Runnable { textSearch() })
		val clsSearchAction = JadxGuiAction(
			ActionModel.CLASS_SEARCH,
			Runnable { SearchDialog.search(this, SearchDialog.SearchPreset.CLASS) },
		)
		val commentSearchAction = JadxGuiAction(
			ActionModel.COMMENT_SEARCH,
			Runnable { SearchDialog.search(this, SearchDialog.SearchPreset.COMMENT) },
		)
		val goToMainActivityAction = JadxGuiAction(ActionModel.GO_TO_MAIN_ACTIVITY, Runnable { goToMainActivity() })
		val goToApplicationAction = JadxGuiAction(ActionModel.GO_TO_APPLICATION, Runnable { goToApplication() })
		val goToAndroidManifestAction = JadxGuiAction(
			ActionModel.GO_TO_ANDROID_MANIFEST,
			Runnable { goToAndroidManifest() },
		)
		val decompileAllAction = JadxGuiAction(ActionModel.DECOMPILE_ALL, Runnable { requestFullDecompilation() })
		val resetCacheAction = JadxGuiAction(ActionModel.RESET_CACHE, Runnable { resetCodeCache() })
		val deobfAction = JadxGuiAction(ActionModel.DEOBF, Runnable { toggleDeobfuscation() })

		deobfToggleBtn = JToggleButton(deobfAction)
		deobfToggleBtn.setSelected(settings.isDeobfuscationOn())
		deobfToggleBtn.setText("")

		deobfMenuItem = JCheckBoxMenuItem(deobfAction)
		deobfMenuItem.setState(settings.isDeobfuscationOn())

		val showLogAction = JadxGuiAction(ActionModel.SHOW_LOG, Runnable { showLogViewer(LogOptions.current()) })
		val aboutAction = JadxGuiAction(ActionModel.ABOUT, Runnable { AboutDialog().setVisible(true) })
		val backAction = JadxGuiAction(ActionModel.BACK, Runnable { navController.navBack() })
		val backVariantAction = JadxGuiAction(ActionModel.BACK_V, Runnable { navController.navBack() })
		val forwardAction = JadxGuiAction(ActionModel.FORWARD, Runnable { navController.navForward() })
		val forwardVariantAction = JadxGuiAction(ActionModel.FORWARD_V, Runnable { navController.navForward() })
		val quarkAction = JadxGuiAction(ActionModel.QUARK, Runnable { QuarkDialog(this).setVisible(true) })
		val debuggerAction = JadxGuiAction(ActionModel.OPEN_DEVICE, Runnable { ADBDialog(this).setVisible(true) })

		val file = JadxMenu(NLS.str("menu.file"), shortcutsController)
		file.setMnemonic(KeyEvent.VK_F)
		file.add(openAction)
		file.add(openProject)
		file.add(addFilesAction)
		file.addSeparator()
		file.add(newProjectAction)
		file.add(saveProjectAction)
		file.add(saveProjectAsAction)
		file.addSeparator()
		file.add(reloadAction)
		file.add(liveReloadMenuItem)
		renameMappings.addMenuActions(file)
		file.addSeparator()
		file.add(exportAction)
		file.addSeparator()
		file.add(recentProjects)
		file.addSeparator()
		file.add(prefsAction)
		file.addSeparator()
		file.add(exitAction)

		val view = JadxMenu(NLS.str("menu.view"), shortcutsController)
		view.setMnemonic(KeyEvent.VK_V)
		view.add(quickTabsAction.makeCheckBoxMenuItem())
		view.add(hexViewerMenu)
		view.add(flatPkgMenuItem)
		view.addSeparator()
		view.add(enablePreviewTabAction.makeCheckBoxMenuItem())
		view.add(syncAction)
		view.add(alwaysSelectOpened)
		view.addSeparator()
		view.add(dockLog)
		view.add(heapUsageBarMenuItem)

		val nav = JadxMenu(NLS.str("menu.navigation"), shortcutsController)
		nav.setMnemonic(KeyEvent.VK_N)
		nav.add(textSearchAction)
		nav.add(clsSearchAction)
		nav.add(commentSearchAction)
		nav.add(goToMainActivityAction)
		nav.add(goToApplicationAction)
		nav.add(goToAndroidManifestAction)
		nav.addSeparator()
		nav.add(backAction)
		nav.add(forwardAction)

		pluginsMenu = JadxMenu(NLS.str("menu.plugins"), shortcutsController)
		pluginsMenu.setMnemonic(KeyEvent.VK_P)
		resetPluginsMenu()

		val tools = JadxMenu(NLS.str("menu.tools"), shortcutsController)
		tools.setMnemonic(KeyEvent.VK_T)
		tools.add(decompileAllAction)
		tools.add(resetCacheAction)
		tools.add(deobfMenuItem)
		tools.add(quarkAction)
		tools.add(debuggerAction)

		val help = JadxMenu(NLS.str("menu.help"), shortcutsController)
		help.setMnemonic(KeyEvent.VK_H)
		help.add(showLogAction)
		if (JadxSystemInfo.IS_LINUX) {
			help.add(JadxGuiAction(ActionModel.CREATE_DESKTOP_ENTRY, Runnable { createDesktopEntry() }))
		}
		if (Jadx.isDevVersion()) {
			help.add(object : AbstractAction("Show sample error report") {
				override fun actionPerformed(e: ActionEvent) {
					ExceptionDialog.throwTestException()
				}
			})
		}
		if (UiUtils.JADX_GUI_DEBUG) {
			val uiWatchDog = JCheckBoxMenuItem(ActionHandler("UI WatchDog", Runnable { UIWatchDog.toggle() }))
			uiWatchDog.setState(UIWatchDog.onStart())
			help.add(uiWatchDog)
		}

		if (JadxSystemInfo.IS_MAC) {
			System.setProperty("apple.laf.useScreenMenuBar", "true")
			Desktop.getDesktop().setAboutHandler { aboutAction.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, "about")) }
		} else {
			help.add(aboutAction)
		}

		menuBar = JadxMenuBar()
		menuBar.add(file)
		menuBar.add(view)
		menuBar.add(nav)
		menuBar.add(tools)
		menuBar.add(pluginsMenu)
		menuBar.add(help)
		setJMenuBar(menuBar)

		flatPkgButton = JToggleButton(Icons.FLAT_PKG)
		flatPkgButton.setSelected(isFlattenPackage)
		val flatPkgAction = ActionListener { toggleFlattenPackage() }
		flatPkgMenuItem.addActionListener(flatPkgAction)
		flatPkgButton.addActionListener(flatPkgAction)
		flatPkgButton.setToolTipText(NLS.str("menu.flatten"))

		updateLink = Link()
		updateLink.setVisible(false)

		val toolbar = JToolBar()
		toolbar.setFloatable(false)
		toolbar.add(openAction)
		toolbar.add(addFilesAction)
		toolbar.addSeparator()
		toolbar.add(reloadAction)
		toolbar.addSeparator()
		toolbar.add(exportAction)
		toolbar.addSeparator()
		toolbar.add(syncAction)
		toolbar.add(flatPkgButton)
		toolbar.add(enablePreviewTabAction.makeToggleButton())
		toolbar.add(quickTabsAction.makeToggleButton())
		toolbar.addSeparator()
		toolbar.add(textSearchAction)
		toolbar.add(clsSearchAction)
		toolbar.add(commentSearchAction)
		toolbar.add(goToMainActivityAction)
		toolbar.add(goToApplicationAction)
		toolbar.add(goToAndroidManifestAction)
		toolbar.addSeparator()
		toolbar.add(backAction)
		toolbar.add(forwardAction)
		toolbar.addSeparator()
		toolbar.add(deobfToggleBtn)
		toolbar.add(quarkAction)
		toolbar.add(debuggerAction)
		toolbar.addSeparator()
		toolbar.add(showLogAction)
		toolbar.addSeparator()
		toolbar.add(prefsAction)
		toolbar.addSeparator()
		toolbar.add(Box.createHorizontalGlue())
		toolbar.add(updateLink)

		mainPanel.add(toolbar, BorderLayout.NORTH)

		nav.add(HiddenMenuItem(backVariantAction))
		nav.add(HiddenMenuItem(forwardVariantAction))

		shortcutsController.bind(backVariantAction)
		shortcutsController.bind(forwardVariantAction)

		addLoadListener(object : ILoadListener {
			override fun update(loaded: Boolean): Boolean {
				textSearchAction.setEnabled(loaded)
				clsSearchAction.setEnabled(loaded)
				commentSearchAction.setEnabled(loaded)
				goToMainActivityAction.setEnabled(loaded)
				goToApplicationAction.setEnabled(loaded)
				goToAndroidManifestAction.setEnabled(loaded)
				backAction.setEnabled(loaded)
				backVariantAction.setEnabled(loaded)
				forwardAction.setEnabled(loaded)
				forwardVariantAction.setEnabled(loaded)
				syncAction.setEnabled(loaded)
				exportAction.setEnabled(loaded)
				saveProjectAsAction.setEnabled(loaded)
				reloadAction.setEnabled(loaded)
				decompileAllAction.setEnabled(loaded)
				deobfAction.setEnabled(loaded)
				quarkAction.setEnabled(loaded)
				debuggerAction.setEnabled(loaded)
				resetCacheAction.setEnabled(loaded)
				return false
			}
		})
	}

	private fun initUI() {
		setMinimumSize(Dimension(200, 150))
		mainPanel = JPanel(BorderLayout())
		treeSplitPane = JSplitPane()
		treeSplitPane.setResizeWeight(SPLIT_PANE_RESIZE_WEIGHT)
		mainPanel.add(treeSplitPane)

		val treeRootNode = DefaultMutableTreeNode(NLS.str("msg.open_file"))
		treeModel = DefaultTreeModel(treeRootNode)
		tree = JTree(treeModel)
		ToolTipManager.sharedInstance().registerComponent(tree)
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)
		tree.setFocusable(false)
		tree.addFocusListener(object : FocusAdapter() {
			override fun focusLost(e: FocusEvent) {
				tree.setFocusable(false)
			}
		})
		tree.addMouseListener(object : MouseAdapter() {
			override fun mousePressed(e: MouseEvent) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (!nodeClickAction(getJNodeUnderMouse(e))) {
						// 点击未被处理 -> 切换到可聚焦模式
						tree.setFocusable(true)
						tree.requestFocus()
					}
				} else if (SwingUtilities.isRightMouseButton(e)) {
					treeRightClickAction(e)
				}
			}
		})
		tree.addKeyListener(object : KeyAdapter() {
			override fun keyPressed(e: KeyEvent) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					nodeClickAction(tree.getLastSelectedPathComponent())
				}
			}
		})
		tree.setCellRenderer(object : DefaultTreeCellRenderer() {
			override fun getTreeCellRendererComponent(
				tree: JTree,
				value: Any?,
				selected: Boolean,
				expanded: Boolean,
				isLeaf: Boolean,
				row: Int,
				focused: Boolean,
			): Component {
				val c = super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row, focused)
				if (value is JNode) {
					NodeLabel.disableHtml(this, value.disableHtml())
					setText(value.makeStringHtml())
					setIcon(value.getIcon())
					setToolTipText(value.getTooltip())
				} else {
					setToolTipText(null)
				}
				if (value is JPackage) {
					isEnabled = value.isEnabled()
				}
				return c
			}
		})
		tree.addTreeWillExpandListener(object : TreeWillExpandListener {
			override fun treeWillExpand(event: TreeExpansionEvent) {
				val path = event.getPath()
				val node = path.getLastPathComponent()
				if (node is JLoadableNode) {
					val loadTask = node.getLoadTask()
					if (loadTask != null) {
						backgroundExecutor.execute(
							TaskWithExtraOnFinish(
								loadTask,
								{
									if (!treeReloading) {
										treeModel.nodeStructureChanged(node)
									}
								},
							),
						)
					}
				}
			}

			override fun treeWillCollapse(event: TreeExpansionEvent) {
				if (!treeReloading) {
					update()
				}
			}
		})

		progressPane = ProgressPanel(this, true)
		issuesPanel = IssuesPanel(this)

		val leftPane = JPanel(BorderLayout())
		val treeScrollPane = JScrollPane(tree)
		treeScrollPane.setMinimumSize(Dimension(100, 150))

		val bottomPane = JPanel(BorderLayout())
		bottomPane.add(issuesPanel, BorderLayout.PAGE_START)
		bottomPane.add(progressPane, BorderLayout.PAGE_END)

		leftPane.add(treeScrollPane, BorderLayout.CENTER)
		leftPane.add(bottomPane, BorderLayout.PAGE_END)
		treeSplitPane.setLeftComponent(leftPane)

		tabbedPane = TabbedPane(this, tabsController)
		tabbedPane.setMinimumSize(Dimension(150, 150))
		TabDndController(tabbedPane, settings)

		quickTabsAndCodeSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
		quickTabsAndCodeSplitPane.setResizeWeight(0.15)
		quickTabsAndCodeSplitPane.setDividerSize(0)
		quickTabsAndCodeSplitPane.setRightComponent(tabbedPane)

		rightSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
		rightSplitPane.setTopComponent(quickTabsAndCodeSplitPane)
		rightSplitPane.setResizeWeight(SPLIT_PANE_RESIZE_WEIGHT)

		treeSplitPane.setRightComponent(rightSplitPane)

		DropTarget(this, DnDConstants.ACTION_COPY, MainDropTarget(this))

		heapUsageBar = HeapUsageBar()
		mainPanel.add(heapUsageBar, BorderLayout.SOUTH)

		bottomSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
		bottomSplitPane.setTopComponent(treeSplitPane)
		bottomSplitPane.setResizeWeight(SPLIT_PANE_RESIZE_WEIGHT)

		mainPanel.add(bottomSplitPane, BorderLayout.CENTER)
		setContentPane(mainPanel)
		setTitle(DEFAULT_TITLE)

		if (UiUtils.JADX_GUI_DEBUG) {
			FlatInspector.install("ctrl shift alt X")
			FlatUIDefaultsInspector.install("ctrl shift alt Y")
		}

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
		addWindowListener(object : WindowAdapter() {
			override fun windowClosing(e: WindowEvent) {
				closeWindow()
			}
		})
	}

	fun setLocationAndPosition() {
		if (settings.loadWindowPos(this)) {
			return
		}
		val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
		val mode = gd.getDisplayMode()
		val trans = gd.getDefaultConfiguration().getDefaultTransform()
		val w = (mode.getWidth() / trans.getScaleX()).toInt()
		val h = (mode.getHeight() / trans.getScaleY()).toInt()
		setBounds(
			(w * BORDER_RATIO).toInt(),
			(h * BORDER_RATIO).toInt(),
			(w * WINDOW_RATIO).toInt(),
			(h * WINDOW_RATIO).toInt(),
		)
		setLocationRelativeTo(null)
	}

	private fun openSettings() {
		openSettings(null)
	}

	private fun openSettings(navigateTo: String?) {
		settingsOpen = true

		val settingsWindow = JadxSettingsWindow(this, settings)
		settingsWindow.addWindowListener(object : WindowAdapter() {
			override fun windowClosed(e: WindowEvent) {
				settingsOpen = false
			}
		})
		if (navigateTo != null) {
			settingsWindow.activatePage(navigateTo)
		}
		settingsWindow.setVisible(true)
	}

	fun isSettingsOpen(): Boolean = settingsOpen

	fun loadSettings() {
		// 排队更新，避免打断当前 UI 任务
		UiUtils.uiRun(Runnable { updateUiSettings() })
	}

	private fun updateUiSettings() {
		var needUpdateUI = false
		val defaultUiFont = UIManager.getFont("defaultFont")
		val uiFont = settings.getUiFont()
		if (uiFont != defaultUiFont) {
			UIManager.put("defaultFont", uiFont)
			setFont(uiFont)
			needUpdateUI = true
		}
		if (LafManager.updateLaf(settings)) {
			needUpdateUI = true
		}
		editorThemeManager.setTheme(settings.getEditorTheme())

		if (UIScale.setZoomFactor(settings.getUiZoom())) {
			needUpdateUI = true
		}
		tree.setFont(settings.getCodeFont())
		tree.setRowHeight(-1)

		tabbedPane.loadSettings()
		logPanel?.loadSettings()
		quickTabsTree?.loadSettings()
		shortcutsController.loadSettings()
		if (needUpdateUI) {
			FlatLaf.updateUI()
		}
	}

	private fun closeWindow() {
		saveAll()
		if (!ensureProjectIsSaved()) {
			return
		}
		UiUtils.bgRun(
			Runnable {
				try {
					settings.setTreeWidth(treeSplitPane.getDividerLocation())
					settings.saveWindowPos(this)
					settings.setMainWindowExtendedState(getExtendedState())
					if (debuggerPanel != null) {
						saveSplittersInfo()
					}
					// 阻塞 UI 线程，避免同步期间设置数据被修改
					UiUtils.uiRunAndWait(Runnable { settings.sync() })

					closeAll()
					UiUtils.uiRunAndWait(
						Runnable {
							heapUsageBar.reset()
							editorThemeManager.unload()
							dispose()
						},
					)
				} catch (e: Exception) {
					LOG.error("Close window error", e)
				} finally {
					backgroundExecutor.dispose()
					System.exit(0)
				}
			},
		)
	}

	private fun saveOpenTabs() {
		project.saveOpenTabs(tabsController.getEditorViewStates())
	}

	private fun restoreOpenTabs(openTabs: List<EditorViewState>) {
		UiUtils.uiThreadGuard()
		if (openTabs.isEmpty()) {
			return
		}
		for (viewState in openTabs) {
			tabsController.restoreEditorViewState(viewState)
		}
		tabsController.notifyRestoreEditorViewStateDone()
	}

	private fun preLoadOpenTabs(openTabs: List<EditorViewState>) {
		UiUtils.notUiThreadGuard()
		for (tabState in openTabs) {
			if (tabState.isHidden()) {
				continue
			}
			val node = tabState.getNode()
			try {
				node.getCodeInfo()
			} catch (e: Exception) {
				LOG.warn("Failed to preload code for node: {}", node, e)
			}
		}
	}

	private fun saveSplittersInfo() {
		settings.setMainWindowVerticalSplitterLoc(bottomSplitPane.getDividerLocation())
		val panel = debuggerPanel
		if (panel != null) {
			settings.setDebuggerStackFrameSplitterLoc(panel.getLeftSplitterLocation())
			settings.setDebuggerVarTreeSplitterLoc(panel.getRightSplitterLocation())
		}
	}

	fun addLoadListener(loadListener: ILoadListener) {
		loadListeners.add(loadListener)
		// 设置初始值
		loadListener.update(loaded)
	}

	fun notifyLoadListeners(loaded: Boolean) {
		this.loaded = loaded
		loadListeners.removeIf { listener -> listener.update(loaded) }
	}

	fun addTreeUpdateListener(listener: (JRoot) -> Unit) {
		treeUpdateListener.add(listener)
	}

	fun getWrapper(): JadxWrapper = wrapper

	fun getProject(): JadxProject = project

	fun getTabbedPane(): TabbedPane = tabbedPane

	fun getTabsController(): TabsController = tabsController

	fun getNavController(): NavigationController = navController

	fun getSettings(): JadxSettings = settings

	fun getCacheObject(): CacheObject = cacheObject

	fun getBackgroundExecutor(): BackgroundExecutor = backgroundExecutor

	fun getTreeRoot(): JRoot = checkNotNull(treeRoot)

	fun getDebuggerPanel(): JDebuggerPanel {
		initDebuggerPanel()
		return checkNotNull(debuggerPanel)
	}

	fun getShortcutsController(): ShortcutsController = shortcutsController

	fun showDebuggerPanel() {
		initDebuggerPanel()
	}

	fun destroyDebuggerPanel() {
		saveSplittersInfo()
		val panel = debuggerPanel
		if (panel != null) {
			panel.setVisible(false)
			debuggerPanel = null
		}
	}

	fun showHeapUsageBar() {
		settings.setShowHeapUsageBar(true)
		heapUsageBar.setVisible(true)
	}

	private fun initDebuggerPanel() {
		if (debuggerPanel == null) {
			val panel = JDebuggerPanel(this)
			debuggerPanel = panel
			panel.loadSettings()
			bottomSplitPane.setBottomComponent(panel)
			var loc = settings.getMainWindowVerticalSplitterLoc()
			if (loc == 0) {
				loc = 300
			}
			bottomSplitPane.setDividerLocation(loc)
		}
	}

	fun showLogViewer(logOptions: LogOptions) {
		UiUtils.uiRun(
			Runnable {
				if (settings.isDockLogViewer()) {
					showDockedLog(logOptions)
				} else {
					LogViewerDialog.open(this, logOptions)
				}
			},
		)
	}

	private fun showDockedLog(logOptions: LogOptions) {
		val currentLogPanel = logPanel
		if (currentLogPanel != null) {
			currentLogPanel.applyLogOptions(logOptions)
			return
		}
		val undock = Runnable {
			hideDockedLog()
			settings.saveDockLogViewer(false)
			LogViewerDialog.open(this, logOptions)
		}
		logPanel = LogPanel(this, logOptions, undock, Runnable { hideDockedLog() })
		rightSplitPane.setBottomComponent(logPanel)
	}

	private fun hideDockedLog() {
		val currentLogPanel = logPanel ?: return
		currentLogPanel.dispose()
		logPanel = null
		rightSplitPane.setBottomComponent(null)
	}

	private fun setQuickTabsVisibility(visible: Boolean) {
		if (visible) {
			if (quickTabsTree == null) {
				quickTabsTree = QuickTabsTree(this)
			}
			quickTabsAndCodeSplitPane.setLeftComponent(quickTabsTree)
			quickTabsAndCodeSplitPane.setDividerSize(5)
		} else {
			quickTabsAndCodeSplitPane.setLeftComponent(null)
			quickTabsAndCodeSplitPane.setDividerSize(0)

			val tree = quickTabsTree
			if (tree != null) {
				tree.dispose()
				quickTabsTree = null
			}
		}
	}

	fun getPluginsMenu(): JMenu = pluginsMenu

	fun resetPluginsMenu() {
		pluginsMenu.removeAll()
		pluginsMenu.add(
			ActionHandler(Runnable { openSettings("PluginSettingsGroup.class") })
				.withNameAndDesc(NLS.str("preferences.plugins.manage")),
		)
	}

	fun addToPluginsMenu(item: Action) {
		if (pluginsMenu.getMenuComponentCount() == 1) {
			pluginsMenu.addSeparator()
		}
		pluginsMenu.add(item)
	}

	private fun createDesktopEntry() {
		if (DesktopEntryUtils.createDesktopEntry()) {
			JOptionPane.showMessageDialog(
				this,
				NLS.str("message.desktop_entry_creation_success"),
				NLS.str("message.success_title"),
				JOptionPane.INFORMATION_MESSAGE,
			)
		} else {
			JOptionPane.showMessageDialog(
				this,
				NLS.str("message.desktop_entry_creation_error"),
				NLS.str("message.errorTitle"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	/** 检查是否存在当前字体无法显示的类/方法/字段名，并提示用户。 */
	private fun checkIfCodeHasNonPrintableChars() {
		if (settings.isRenamePrintable() || settings.isDeobfuscationOn()) {
			return
		}

		if (showUndisplayedCharsDialog) {
			return
		}

		val nonDisplayString = StringBuilder()

		val classes = wrapper.getRootNode().getClasses(true)
		val font = settings.getCodeFont()
		var hasNonDisplayable = false

		for (cls in classes) {
			val className = cls.rawName
			if (!FontUtils.canStringBeDisplayed(className, font)) {
				hasNonDisplayable = true
				nonDisplayString.append(className)
				nonDisplayString.append("\n")
			}

			for (methodNode in cls.methods) {
				val methodName = methodNode.getName()
				if (!FontUtils.canStringBeDisplayed(methodName, font)) {
					hasNonDisplayable = true
					nonDisplayString.append(methodName)
					nonDisplayString.append("\n")
				}
			}

			for (fieldNode in cls.fields) {
				val fieldName = fieldNode.getName()
				if (!FontUtils.canStringBeDisplayed(fieldName, font)) {
					hasNonDisplayable = true
					nonDisplayString.append(fieldName)
					nonDisplayString.append("\n")
				}
			}
		}

		if (hasNonDisplayable) {
			showUndisplayedCharsDialog = true
			val dialogResult = JOptionPane.showConfirmDialog(
				this,
				NLS.str("msg.non_displayable_chars", font.getFontName()),
				NLS.str("msg.warning_title"),
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
			)
			if (dialogResult == JOptionPane.YES_OPTION) {
				tabsController.selectTab(UndisplayedStringsNode(nonDisplayString.toString()))
			}
		}
	}

	fun getRenameMappings(): RenameMappingsGui = renameMappings

	fun getCacheManager(): CacheManager = cacheManager

	fun getEditorThemeManager(): EditorThemeManager = editorThemeManager

	fun events(): JadxGuiEventsImpl = events

	private fun initHexViewMenu() {
		hexViewerMenu.setEnabled(false)

		val showInspectorAction = JadxGuiAction(
			ActionModel.HEX_VIEWER_SHOW_INSPECTOR,
			Runnable { sendActionsToHexViewer(ActionModel.HEX_VIEWER_SHOW_INSPECTOR) },
		)
		val showInspectorMenuItem = JCheckBoxMenuItem(showInspectorAction)

		val changeEncoding = JadxGuiAction(
			ActionModel.HEX_VIEWER_CHANGE_ENCODING,
			Runnable { sendActionsToHexViewer(ActionModel.HEX_VIEWER_CHANGE_ENCODING) },
		)
		val goToAddress = JadxGuiAction(
			ActionModel.HEX_VIEWER_GO_TO_ADDRESS,
			Runnable { sendActionsToHexViewer(ActionModel.HEX_VIEWER_GO_TO_ADDRESS) },
		)

		val findAction = JadxGuiAction(
			ActionModel.HEX_VIEWER_FIND,
			Runnable { sendActionsToHexViewer(ActionModel.HEX_VIEWER_FIND) },
		)

		hexViewerMenu.add(showInspectorMenuItem)
		hexViewerMenu.add(changeEncoding)
		hexViewerMenu.add(goToAddress)
		hexViewerMenu.addSeparator()
		hexViewerMenu.add(findAction)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(MainWindow::class.java)

		private const val DEFAULT_TITLE = "jadx-gui"

		private const val BORDER_RATIO = 0.15
		private const val WINDOW_RATIO = 1.0 - BORDER_RATIO * 2
		const val SPLIT_PANE_RESIZE_WEIGHT = 0.15

		/** 根据已加载文件推导默认工程文件路径（同名 `.jadx`）。 */
		private fun getProjectPathForFile(loadedFile: Path): Path {
			val fileName = loadedFile.getFileName().toString() + "." + JadxProject.PROJECT_EXTENSION
			return loadedFile.resolveSibling(fileName)
		}
	}
}
