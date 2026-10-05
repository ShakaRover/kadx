package kadx.gui.plugins.mappings

import kadx.api.args.UserRenamesMappingsMode
import kadx.api.plugins.utils.CommonFileUtils
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.Utils
import kadx.gui.jobs.TaskStatus
import kadx.gui.settings.KadxProject
import kadx.gui.settings.KadxSettings
import kadx.gui.treemodel.JNode
import kadx.gui.treemodel.JRoot
import kadx.gui.ui.MainWindow
import kadx.gui.ui.filedialog.FileDialogWrapper
import kadx.gui.ui.filedialog.FileOpenMode
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.tab.TabbedPane
import kadx.gui.utils.ILoadListener
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.gui.utils.ui.ActionHandler
import kadx.plugins.mappings.RenameMappingsOptions
import kadx.plugins.mappings.save.MappingExporter
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.format.MappingFormat
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Locale
import javax.swing.Action
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JOptionPane

/**
 * 「重命名映射」的 GUI 逻辑：打开/保存映射文件、把映射文件挂到输入树、自动保存等。
 *
 * **做什么**：在「文件」菜单中加入打开/保存/另存为/关闭映射的入口；
 * 监听工程加载与重命名事件，按设置自动保存；把映射文件节点加入输入树。
 *
 * **线程模型**：保持原 Swing 模型，耗时导出交给 `BackgroundExecutor`。
 */
class RenameMappingsGui(private val mainWindow: MainWindow) {

	private var renamesChanged = false
	private var mappingNode: JInputMapping? = null

	@Transient
	private var openMappingsMenu: JMenu? = null

	@Transient
	private var saveMappingsAction: Action? = null

	@Transient
	private var saveMappingsAsMenu: JMenu? = null

	@Transient
	private var closeMappingsAction: Action? = null

	init {
		mainWindow.addLoadListener(object : ILoadListener {
			override fun update(loaded: Boolean): Boolean = onLoad(loaded)
		})
		mainWindow.addTreeUpdateListener { treeRoot -> treeUpdate(treeRoot) }
	}

	/** 向「文件」菜单加入打开/保存/另存为/关闭映射的入口。 */
	fun addMenuActions(menu: JMenu) {
		val openMenu = JMenu(NLS.str("file.open_mappings"))
		openMenu.add(
			ActionHandler(Runnable { openMappings(MappingFormat.PROGUARD_FILE, true) })
				.withNameAndDesc("Proguard (inverted)"),
		)
		openMenu.add(
			ActionHandler(Runnable { openMappings(MappingFormat.PROGUARD_FILE, false) })
				.withNameAndDesc("Proguard"),
		)
		openMappingsMenu = openMenu

		val saveAction = ActionHandler(Runnable { saveMappings() }).withNameAndDesc(NLS.str("file.save_mappings"))
		saveMappingsAction = saveAction

		val saveAsMenu = JMenu(NLS.str("file.save_mappings_as"))
		saveMappingsAsMenu = saveAsMenu

		for (mappingFormat in MappingFormat.values()) {
			if (mappingFormat != MappingFormat.PROGUARD_FILE) {
				openMenu.add(
					ActionHandler(Runnable { openMappings(mappingFormat, false) })
						.withNameAndDesc(mappingFormat.name),
				)
			}
			saveAsMenu.add(
				ActionHandler(Runnable { saveMappingsAs(mappingFormat) })
					.withNameAndDesc(mappingFormat.name),
			)
		}

		val closeAction = ActionHandler(Runnable { closeMappingsAndRemoveFromProject() })
			.withNameAndDesc(NLS.str("file.close_mappings"))
		closeMappingsAction = closeAction

		menu.addSeparator()
		menu.add(openMenu)
		menu.add(saveAction)
		menu.add(saveAsMenu)
		menu.add(closeAction)
	}

	private fun onLoad(loaded: Boolean): Boolean {
		renamesChanged = false
		mappingNode = null
		if (loaded) {
			val rootNode: RootNode = mainWindow.getWrapper().rootNode
			rootNode.registerCodeDataUpdateListener { onRename() }
		} else {
			// 工程或窗口关闭
			val project: KadxProject = mainWindow.getProject()
			val settings: KadxSettings = mainWindow.getSettings()
			if (project.mappingsPath != null &&
				settings.userRenamesMappingsMode == UserRenamesMappingsMode.READ_AND_AUTOSAVE_BEFORE_CLOSING
			) {
				saveMappings()
			}
		}
		return false
	}
	private fun onRename() {
		val project: KadxProject = mainWindow.getProject()
		val settings: KadxSettings = mainWindow.getSettings()
		if (project.mappingsPath != null &&
			settings.userRenamesMappingsMode == UserRenamesMappingsMode.READ_AND_AUTOSAVE_EVERY_CHANGE
		) {
			saveMappings()
		} else {
			renamesChanged = true
			UiUtils.uiRun(Runnable { mainWindow.update() })
		}
	}

	/** 根据工程加载状态与脏标记刷新菜单项可用性。 */
	fun onUpdate(loaded: Boolean) {
		val project: KadxProject = mainWindow.getProject()
		openMappingsMenu?.setEnabled(loaded)
		saveMappingsAction?.setEnabled(loaded && renamesChanged && project.mappingsPath != null)
		saveMappingsAsMenu?.setEnabled(loaded)
		closeMappingsAction?.setEnabled(project.mappingsPath != null)
	}
	private fun treeUpdate(treeRoot: JRoot) {
		if (mappingNode != null) {
			// 已经添加过
			return
		}
		val mappingsPath: Path = mainWindow.getProject().mappingsPath ?: return
		val node: JNode = treeRoot.followStaticPath("JInputs")
		val currentNode = node.removeNode { it.javaClass == JInputMapping::class.java }
		if (currentNode != null) {
			// 关闭已打开的标签页
			val tabbedPane: TabbedPane = mainWindow.getTabbedPane()
			val openedTab: ContentPanel? = tabbedPane.getTabByNode(currentNode)
			if (openedTab != null) {
				tabbedPane.closeCodePanel(openedTab)
			}
		}
		val newMappingNode = JInputMapping(mappingsPath)
		mappingNode = newMappingNode
		node.add(newMappingNode)
	}
	private fun openMappings(mappingFormat: MappingFormat, inverted: Boolean) {
		val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.CUSTOM_OPEN)
		fileDialog.setTitle(NLS.str("file.open_mappings"))
		if (mappingFormat.hasSingleFile()) {
			fileDialog.setFileExtList(Collections.singletonList(mappingFormat.fileExt))
			fileDialog.setSelectionMode(JFileChooser.FILES_ONLY)
		} else {
			fileDialog.setSelectionMode(JFileChooser.DIRECTORIES_ONLY)
		}
		val selectedPaths = fileDialog.show()
		if (selectedPaths.size != 1) {
			return
		}
		val filePath = selectedPaths[0]
		LOG.info("Loading mappings from: {}", filePath.toAbsolutePath())
		val project: KadxProject = mainWindow.getProject()
		project.setMappingsPath(filePath)
		project.updatePluginOptions { options ->
			options[RenameMappingsOptions.FORMAT_OPT] = mappingFormat.name
			options[RenameMappingsOptions.INVERT_OPT] = if (inverted) "yes" else "no"
		}
		mainWindow.reopen()
	}

	/** 从工程中移除映射文件并重新打开。 */
	fun closeMappingsAndRemoveFromProject() {
		mainWindow.getProject().setMappingsPath(null)
		mainWindow.reopen()
	}
	private fun saveMappings() {
		renamesChanged = false
		saveInBackground(
			currentMappingFormat,
			checkNotNull(mainWindow.getProject().mappingsPath),
			{ mainWindow.update() },
		)
	}

	private fun saveMappingsAs(mappingFormat: MappingFormat) {
		val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.CUSTOM_SAVE)
		fileDialog.setTitle(NLS.str("file.save_mappings_as"))
		if (mappingFormat.hasSingleFile()) {
			val currentDir = Utils.getOrElse(fileDialog.currentDir, CommonFileUtils.CWD_PATH)
			fileDialog.setSelectedFile(currentDir.resolve("mappings." + mappingFormat.fileExt))
			fileDialog.setFileExtList(Collections.singletonList(mappingFormat.fileExt))
			fileDialog.setSelectionMode(JFileChooser.FILES_ONLY)
		} else {
			fileDialog.setSelectionMode(JFileChooser.DIRECTORIES_ONLY)
		}
		val selectedPaths = fileDialog.show()
		if (selectedPaths.size != 1) {
			return
		}
		val selectedPath = selectedPaths[0]
		val savePath: Path
		// 缺少扩展名时补全
		if (mappingFormat.hasSingleFile() &&
			!selectedPath.getFileName().toString().lowercase(Locale.ROOT).endsWith(checkNotNull(mappingFormat.fileExt))
		) {
			savePath = selectedPath.resolveSibling(selectedPath.getFileName().toString() + "." + mappingFormat.fileExt)
		} else {
			savePath = selectedPath
		}
		// 目标已存在（且不是空目录）时弹出覆盖确认
		if (Files.exists(savePath)) {
			var emptyDir = false
			try {
				Files.list(savePath).use { entries -> emptyDir = entries.findFirst().isEmpty() }
			} catch (ignored: IOException) {
				// 忽略：无法列出目录时按非空处理
			}
			if (!emptyDir) {
				val res = JOptionPane.showConfirmDialog(
					mainWindow,
					NLS.str("confirm.save_as_message", savePath.getFileName()),
					NLS.str("confirm.save_as_title"),
					JOptionPane.YES_NO_OPTION,
				)
				if (res == JOptionPane.NO_OPTION) {
					return
				}
			}
		}
		LOG.info("Saving mappings to: {}", savePath.toAbsolutePath())
		val project: KadxProject = mainWindow.getProject()
		project.setMappingsPath(savePath)
		project.updatePluginOptions { options ->
			options[RenameMappingsOptions.FORMAT_OPT] = mappingFormat.name
			options[RenameMappingsOptions.INVERT_OPT] = "no"
		}
		saveInBackground(
			mappingFormat,
			savePath,
			{
				mappingNode = null
				mainWindow.reloadTree()
			},
		)
	}
	private fun saveInBackground(mappingFormat: MappingFormat, savePath: Path, onFinishUiRunnable: (TaskStatus) -> Unit) {
		mainWindow.getBackgroundExecutor().execute(
			NLS.str("progress.save_mappings"),
			Runnable {
				MappingExporter(mainWindow.getWrapper().rootNode)
					.exportMappings(savePath, mainWindow.getProject().codeData, mappingFormat)
			},
			onFinishUiRunnable,
		)
	}

	private val currentMappingFormat: MappingFormat get() {
		val project: KadxProject = mainWindow.getProject()
		val fmtStr = project.getPluginOption(RenameMappingsOptions.FORMAT_OPT)
		if (fmtStr != null) {
			return MappingFormat.valueOf(fmtStr)
		}
		val mappingsPath = project.mappingsPath
		try {
			return checkNotNull(MappingReader.detectFormat(mappingsPath))
		} catch (e: IOException) {
			throw RuntimeException("Failed to detect mapping format for: $mappingsPath")
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(RenameMappingsGui::class.java)
	}
}
