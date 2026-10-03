package jadx.gui.settings

import com.google.gson.Gson
import jadx.api.JadxArgs
import jadx.api.data.ICodeComment
import jadx.api.data.ICodeRename
import jadx.api.data.IJavaCodeRef
import jadx.api.data.IJavaNodeRef
import jadx.api.data.impl.JadxCodeComment
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxCodeRef
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.api.plugins.utils.CommonFileUtils
import jadx.core.utils.GsonUtils.defaultGsonBuilder
import jadx.core.utils.GsonUtils.interfaceReplace
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.gui.settings.data.ProjectData
import jadx.gui.settings.data.SaveOptionEnum
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.EditorViewState
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.utils.NLS
import jadx.gui.utils.RelativePathTypeAdapter
import jadx.gui.utils.ui.ActionMessageBox
import jadx.gui.utils.ui.ActionMessageBox.Action
import org.apache.commons.lang3.StringUtils
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.StringJoiner
import java.util.function.Consumer

/**
 * 一个 jadx 项目：持有 [ProjectData] 并在其变化时驱动保存与界面刷新。
 *
 * **做什么**：维护输入文件、类树展开、注释/重命名、打开标签、搜索历史、缓存目录等；
 * 通过 Gson 把 [ProjectData] 读写到 `.jadx` 项目文件。
 *
 * **为什么保留显式 getter/setter**：`MainWindow`、`CacheManager` 等大量 Java 调用方
 * 按 `getFilePaths()` / `isSaved()` 等命名访问，必须保持 JVM 方法名不变。
 */
class JadxProject private constructor(
	private val mainWindow: MainWindow,
	private val data: ProjectData,
) {
	private val tabStateViewAdapter: TabStateViewAdapter = TabStateViewAdapter()

	private var name: String = "New Project"
	private var inputsHash: String = ""
	private var projectPath: Path? = null

	private var initial: Boolean = true
	private var saved: Boolean = false

	private var cacheDir: Path? = null

	constructor(mainWindow: MainWindow) : this(mainWindow, ProjectData())

	/** 把项目中的输入文件、映射与插件选项填充进 [jadxArgs]。 */
	fun fillJadxArgs(jadxArgs: JadxArgs) {
		jadxArgs.inputFiles = FileUtils.toFiles(getFilePaths()).toMutableList()
		if (jadxArgs.userRenamesMappingsPath == null) {
			jadxArgs.userRenamesMappingsPath = getMappingsPath()
		}
		jadxArgs.codeData = getCodeData()
		@Suppress("UNCHECKED_CAST")
		(jadxArgs.pluginOptions as MutableMap<String, String>).putAll(data.getPluginOptions())
	}

	/** 项目工作目录（项目文件所在目录，或首个输入文件所在目录）。 */
	fun getWorkingDir(): Path? {
		val path = projectPath
		if (path != null) {
			return path.toAbsolutePath().parent
		}
		val files = data.getFiles()
		if (files.isNotEmpty()) {
			return files[0].toAbsolutePath().parent
		}
		return null
	}

	/** @return 项目文件路径；项目尚未保存时返回 `null` */
	fun getProjectPath(): Path? = projectPath

	private fun setProjectPath(projectPath: Path) {
		this.projectPath = projectPath
		this.name = CommonFileUtils.removeFileExtension(projectPath.fileName.toString())
	}

	fun getFilePaths(): List<Path> = data.getFiles()

	fun setFilePaths(files: List<Path>) {
		if (files == getFilePaths()) {
			return
		}
		if (files.isEmpty()) {
			data.setFiles(files)
			name = ""
			inputsHash = ""
		} else {
			val inputs = verifyInputFiles(files)
			Collections.sort(inputs)
			data.setFiles(inputs)
			name = buildProjectName(inputs)
			inputsHash = FileUtils.buildInputsHash(inputs)
		}
		changed()
	}

	/** 重新校验输入文件是否存在，移除不存在的文件。 */
	fun verifyFiles() {
		setFilePaths(verifyInputFiles(getFilePaths()))
	}

	fun getInputsHash(): String = inputsHash

	fun setTreeExpansions(list: List<String>) {
		if (list == data.getTreeExpansionsV2()) {
			return
		}
		data.setTreeExpansionsV2(list)
		changed()
	}

	fun getTreeExpansions(): List<String> = data.getTreeExpansionsV2()

	fun getCodeData(): JadxCodeData = data.getCodeData()

	fun setCodeData(codeData: JadxCodeData) {
		data.setCodeData(codeData)
		changed()
	}

	/** 保存当前打开的标签页状态到项目数据。 */
	fun saveOpenTabs(tabs: List<EditorViewState>) {
		val tabStateList = tabs.mapNotNull { tabStateViewAdapter.build(it) }
		if (data.setOpenTabs(tabStateList)) {
			changed()
		}
	}

	/** 从项目数据恢复打开的标签页。 */
	fun getOpenTabs(mw: MainWindow): List<EditorViewState> {
		tabStateViewAdapter.setCustomAdapters(mw.wrapper.guiPluginsContext.tabStatePersistAdapters)
		return data.getOpenTabs().mapNotNull { tabStateViewAdapter.load(mw, it) }
	}

	fun getMappingsPath(): Path? = data.getMappingsPath()

	fun setMappingsPath(mappingsPath: Path?) {
		data.setMappingsPath(mappingsPath)
		changed()
	}

	/** 不直接暴露选项 map，以便拦截修改并触发保存。 */
	fun updatePluginOptions(update: Consumer<MutableMap<String, String>>) {
		update.accept(data.getPluginOptions())
		changed()
	}

	fun getPluginOption(key: String): String? = data.getPluginOptions()[key]

	fun getCacheDir(): Path {
		val cached = cacheDir
		if (cached != null) {
			return cached
		}
		val resolved = resolveCachePath(data.getCacheDir())
		cacheDir = resolved
		return resolved
	}

	fun resetCacheDir() {
		cacheDir = resolveCachePath(null)
	}

	private fun resolveCachePath(cacheDirStr: String?): Path {
		val cacheManager = mainWindow.cacheManager
		val newCacheDir = cacheManager.getCacheDir(this, cacheDirStr)
		val newCacheStr = cacheManager.buildCacheDirStr(newCacheDir)
		if (newCacheStr != cacheDirStr) {
			data.setCacheDir(newCacheStr)
			changed()
		}
		return newCacheDir
	}

	fun isEnableLiveReload(): Boolean = data.isEnableLiveReload()

	fun setEnableLiveReload(newValue: Boolean) {
		if (newValue != data.isEnableLiveReload()) {
			data.setEnableLiveReload(newValue)
			changed()
		}
	}

	fun getSearchHistory(): MutableList<String> = data.getSearchHistory()

	fun addToSearchHistory(str: String?) {
		if (str.isNullOrEmpty()) {
			return
		}
		val list = data.getSearchHistory()
		if (list.isNotEmpty() && list[0] == str) {
			return
		}
		list.remove(str)
		list.add(0, str)
		if (list.size > SEARCH_HISTORY_LIMIT) {
			list.removeAt(list.size - 1)
		}
		data.setSearchHistory(list)
		changed()
	}

	fun setSearchResourcesFilter(searchResourcesFilter: String) {
		data.setSearchResourcesFilter(searchResourcesFilter)
	}

	fun getSearchResourcesFilter(): String = data.getSearchResourcesFilter()

	fun setSearchResourcesSizeLimit(searchResourcesSizeLimit: Int) {
		data.setSearchResourcesSizeLimit(searchResourcesSizeLimit)
	}

	fun getSearchResourcesSizeLimit(): Int = data.getSearchResourcesSizeLimit()
	private fun changed() {
		val settings: JadxSettings? = mainWindow.settings
		if (settings != null && settings.getSaveOption() == SaveOptionEnum.ALWAYS) {
			save()
		} else {
			saved = false
		}
		initial = false
		mainWindow.updateProject(this)
	}

	fun getName(): String = name

	fun isSaveFileSelected(): Boolean = projectPath != null

	fun isSaved(): Boolean = saved

	fun isInitial(): Boolean = initial

	fun saveAs(path: Path) {
		mainWindow.cacheManager.projectPathUpdate(this, path)
		setProjectPath(path)
		save()
	}

	fun save() {
		val savePath = getProjectPath() ?: return
		val basePath = checkNotNull(savePath.toAbsolutePath().parent) { "Can't resolve project base path: $savePath" }
		try {
			Files.newBufferedWriter(savePath, StandardCharsets.UTF_8).use { writer: Writer ->
				buildGson(basePath).toJson(data, writer)
				saved = true
			}
		} catch (e: Exception) {
			throw RuntimeException("Error saving project", e)
		}
	}

	private fun verifyInputFiles(inputFiles: List<Path>): MutableList<Path> {
		val files = ArrayList(inputFiles)
		for (p in inputFiles) {
			if (!Files.exists(p)) {
				files.remove(p)
				ActionMessageBox(
					mainWindow,
					NLS.str("project.dialog_title"),
					NLS.str("project.file_not_found") + ":\n" + p.toAbsolutePath(),
					Action(
						NLS.str("project.file_not_found.exclude"),
						Runnable {
							// 默认动作：文件已被排除，无需处理
						},
					),
					Action(
						NLS.str("project.file_not_found.open_another"),
						Runnable {
							files.addAll(FileDialogWrapper(mainWindow, FileOpenMode.ADD).show())
						},
					),
				).show()
			}
		}
		if (files.isEmpty()) {
			ActionMessageBox(
				mainWindow,
				NLS.str("project.dialog_title"),
				NLS.str("project.empty"),
				Action(
					NLS.str("project.empty.add_files"),
					Runnable {
						files.addAll(FileDialogWrapper(mainWindow, FileOpenMode.ADD).show())
					},
				),
				Action(
					NLS.str("project.empty.close"),
					Runnable {
						// 空项目不会被打开
					},
				),
			).show()
		}
		return files
	}
	companion object {
		const val PROJECT_EXTENSION: String = "jadx"

		private const val SEARCH_HISTORY_LIMIT: Int = 30

		private fun buildProjectName(files: List<Path>): String {
			val joiner = StringJoiner("_")
			for (p in files) {
				val fileNamePart = p.fileName
				if (fileNamePart == null) {
					joiner.add(p.toString())
					continue
				}
				val fileName = fileNamePart.toString()
				if (!fileName.endsWith(".jadx.kts")) {
					joiner.add(CommonFileUtils.removeFileExtension(fileName))
				}
			}
			return StringUtils.abbreviate(joiner.toString(), 100)
		}

		@JvmStatic
		fun load(mainWindow: MainWindow, path: Path): JadxProject {
			val projectData = loadProjectData(path)
			val project = JadxProject(mainWindow, projectData)
			project.saved = true
			project.setProjectPath(path)
			project.setFilePaths(project.getFilePaths())
			return project
		}

		@JvmStatic
		fun loadProjectData(path: Path): ProjectData {
			val basePath = checkNotNull(path.toAbsolutePath().parent) { "Can't resolve project base path: $path" }
			try {
				Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader: Reader ->
					return buildGson(basePath).fromJson(reader, ProjectData::class.java)
				}
			} catch (e: Exception) {
				throw JadxRuntimeException("Failed to load project file: $path", e)
			}
		}

		private fun buildGson(basePath: Path): Gson = defaultGsonBuilder()
			.registerTypeHierarchyAdapter(Path::class.java, RelativePathTypeAdapter(basePath))
			.registerTypeAdapter(ICodeComment::class.java, interfaceReplace(JadxCodeComment::class.java))
			.registerTypeAdapter(ICodeRename::class.java, interfaceReplace(JadxCodeRename::class.java))
			.registerTypeAdapter(IJavaNodeRef::class.java, interfaceReplace(JadxNodeRef::class.java))
			.registerTypeAdapter(IJavaCodeRef::class.java, interfaceReplace(JadxCodeRef::class.java))
			.create()
	}
}
