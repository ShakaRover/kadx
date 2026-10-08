package kadx.gui.settings

import com.google.gson.Gson
import kadx.api.KadxArgs
import kadx.api.data.ICodeComment
import kadx.api.data.ICodeRename
import kadx.api.data.IJavaCodeRef
import kadx.api.data.IJavaNodeRef
import kadx.api.data.impl.KadxCodeComment
import kadx.api.data.impl.KadxCodeData
import kadx.api.data.impl.KadxCodeRef
import kadx.api.data.impl.KadxCodeRename
import kadx.api.data.impl.KadxNodeRef
import kadx.api.plugins.utils.CommonFileUtils
import kadx.core.utils.GsonUtils
import kadx.core.utils.GsonUtils.defaultGsonBuilder
import kadx.core.utils.GsonUtils.interfaceReplace
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.files.FileUtils
import kadx.gui.settings.data.ProjectData
import kadx.gui.settings.data.SaveOptionEnum
import kadx.gui.ui.MainWindow
import kadx.gui.ui.codearea.EditorViewState
import kadx.gui.ui.filedialog.FileDialogWrapper
import kadx.gui.ui.filedialog.FileOpenMode
import kadx.gui.utils.NLS
import kadx.gui.utils.RelativePathTypeAdapter
import kadx.gui.utils.ui.ActionMessageBox
import kadx.gui.utils.ui.ActionMessageBox.Action
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.StringJoiner

/**
 * 一个 kadx 项目：持有 [ProjectData] 并在其变化时驱动保存与界面刷新。
 *
 * **做什么**：维护输入文件、类树展开、注释/重命名、打开标签、搜索历史、缓存目录等；
 * 通过 Gson 把 [ProjectData] 读写到 `.kadx` 项目文件。
 *
 * **为什么保留显式 getter/setter**：`MainWindow`、`CacheManager` 等大量 Java 调用方
 * 按 `getFilePaths()` / `isSaved()` 等命名访问，必须保持 JVM 方法名不变。
 */
class KadxProject private constructor(
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

	/** 把项目中的输入文件、映射与插件选项填充进 [kadxArgs]。 */
	fun fillKadxArgs(kadxArgs: KadxArgs) {
		kadxArgs.inputFiles = FileUtils.toFiles(filePaths).toMutableList()
		if (kadxArgs.userRenamesMappingsPath == null) {
			kadxArgs.userRenamesMappingsPath = mappingsPath
		}
		kadxArgs.codeData = codeData
		@Suppress("UNCHECKED_CAST")
		(kadxArgs.pluginOptions as MutableMap<String, String>).putAll(data.getPluginOptions())
	}

	/** 项目工作目录（项目文件所在目录，或首个输入文件所在目录）。 */
	val workingDir: Path? get() {
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

	val filePaths: List<Path> get() = data.getFiles()

	fun setFilePaths(files: List<Path>) {
		if (files == filePaths) {
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
		setFilePaths(verifyInputFiles(filePaths))
	}

	fun getInputsHash(): String = inputsHash

	fun setTreeExpansions(list: List<String>) {
		if (list == data.getTreeExpansionsV2()) {
			return
		}
		data.setTreeExpansionsV2(list)
		changed()
	}

	val treeExpansions: List<String> get() = data.getTreeExpansionsV2()

	val codeData: KadxCodeData get() = data.getCodeData()

	fun setCodeData(codeData: KadxCodeData) {
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
		val pluginsContext = mw.getGuiPluginsManager().getPluginsContext()
		tabStateViewAdapter.setCustomAdapters(pluginsContext.getTabStatePersistAdapters())
		return data.getOpenTabs().mapNotNull { tabStateViewAdapter.load(mw, it) }
	}

	val mappingsPath: Path? get() = data.getMappingsPath()

	fun setMappingsPath(mappingsPath: Path?) {
		data.setMappingsPath(mappingsPath)
		changed()
	}

	/** 不直接暴露选项 map，以便拦截修改并触发保存。 */
	fun updatePluginOptions(update: (MutableMap<String, String>) -> Unit) {
		update(data.getPluginOptions())
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
		val cacheManager = mainWindow.getCacheManager()
		val newCacheDir = cacheManager.getCacheDir(this, cacheDirStr)
		val newCacheStr = cacheManager.buildCacheDirStr(newCacheDir)
		if (newCacheStr != cacheDirStr) {
			data.setCacheDir(newCacheStr)
			changed()
		}
		return newCacheDir
	}

	val isEnableLiveReload: Boolean get() = data.isEnableLiveReload

	fun setEnableLiveReload(newValue: Boolean) {
		if (newValue != data.isEnableLiveReload) {
			data.setEnableLiveReload(newValue)
			changed()
		}
	}

	val searchHistory: MutableList<String> get() = data.getSearchHistory()

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

	val searchResourcesFilter: String get() = data.getSearchResourcesFilter()

	fun setSearchResourcesSizeLimit(searchResourcesSizeLimit: Int) {
		data.setSearchResourcesSizeLimit(searchResourcesSizeLimit)
	}

	val searchResourcesSizeLimit: Int get() = data.getSearchResourcesSizeLimit()
	private fun changed() {
		val settings: KadxSettings? = mainWindow.getSettings()
		if (settings != null && settings.saveOption == SaveOptionEnum.ALWAYS) {
			save()
		} else {
			saved = false
		}
		initial = false
		mainWindow.updateProject(this)
	}

	fun getName(): String = name

	val isSaveFileSelected: Boolean get() = projectPath != null

	val isSaved: Boolean get() = saved

	val isInitial: Boolean get() = initial

	fun saveAs(path: Path) {
		mainWindow.getCacheManager().projectPathUpdate(this, path)
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
		private val LOG = LoggerFactory.getLogger(KadxProject::class.java)
		const val PROJECT_EXTENSION: String = "kadx"

		/** 旧 jadx 项目扩展名（读取兼容；新保存一律用 [.PROJECT_EXTENSION]）。 */
		const val LEGACY_PROJECT_EXTENSION: String = "jadx"

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
				if (!fileName.endsWith(".kadx.kts")) {
					joiner.add(CommonFileUtils.removeFileExtension(fileName))
				}
			}
			return StringUtils.abbreviate(joiner.toString(), 100)
		}

		fun load(mainWindow: MainWindow, path: Path): KadxProject {
			val projectData = loadProjectData(path)
			val project = KadxProject(mainWindow, projectData)
			project.saved = true
			project.setProjectPath(path)
			project.setFilePaths(project.filePaths)
			return project
		}

		fun loadProjectData(path: Path): ProjectData {
			val basePath = checkNotNull(path.toAbsolutePath().parent) { "Can't resolve project base path: $path" }
			try {
				Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader: Reader ->
					val data: ProjectData? = buildGson(basePath).fromJson(reader, ProjectData::class.java)
					if (data == null) {
						// 项目文件为空或损坏（如上次会话崩溃时被截断，Gson 对空文档返回 null，
						// 上游 Java 不检查、Kotlin 非空声明触发内在空检查）：按空项目处理，
						// 用户可重新添加输入文件，而不是让整个项目加载失败
						LOG.warn("Project file is empty or corrupted, loading as empty project: {}", path)
						return ProjectData()
					}
					return data
				}
			} catch (e: Exception) {
				throw KadxRuntimeException("Failed to load project file: $path", e)
			}
		}

		private fun buildGson(basePath: Path): Gson = defaultGsonBuilder()
			.registerTypeHierarchyAdapter(Path::class.java, RelativePathTypeAdapter(basePath))
			.registerTypeAdapter(ICodeComment::class.java, interfaceReplace(KadxCodeComment::class.java))
			.registerTypeAdapter(ICodeRename::class.java, interfaceReplace(KadxCodeRename::class.java))
			.registerTypeAdapter(IJavaNodeRef::class.java, interfaceReplace(KadxNodeRef::class.java))
			.registerTypeAdapter(IJavaCodeRef::class.java, interfaceReplace(KadxCodeRef::class.java))
			.create()
	}
}
