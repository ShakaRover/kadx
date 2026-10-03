package jadx.gui.cache.manager

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jadx.api.plugins.utils.CommonFileUtils
import jadx.core.utils.GsonUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.gui.settings.JadxProject
import jadx.gui.settings.JadxSettings
import jadx.gui.utils.files.JadxFiles
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Collections

/**
 * 项目磁盘缓存的登记与清理管理器。
 *
 * **做什么**：维护 `caches.json` 中的缓存记录列表（项目路径 -> 缓存目录），
 * 负责为项目计算/校验缓存目录、在项目路径变化时迁移记录、以及删除不再使用的缓存。
 *
 * **线程模型（阶段 5.1 保持不变）**：[loadCaches]、[saveCaches]、[removeCacheEntry]
 * 仍在 `this` 上加锁（[Synchronized]），与原 Java `synchronized` 方法一致。
 */
class CacheManager(private val settings: JadxSettings) {

	private val cacheMap: MutableMap<String, CacheEntry> = loadCaches()

	/**
	 * 取项目的缓存目录：
	 * - 未指定 [cacheDirStr] 时新建缓存目录并登记；
	 * - 指定时校验记录，必要时更新或删除旧目录。
	 */
	fun getCacheDir(project: JadxProject, cacheDirStr: String?): Path {
		if (cacheDirStr == null) {
			val newProjectCacheDir = buildCacheDir(project)
			addEntry(projectToKey(project), newProjectCacheDir)
			return newProjectCacheDir
		}
		val cacheDir = resolveCacheDirStr(cacheDirStr, project.getProjectPath())
		return verifyEntry(project, cacheDir)
	}

	/** 项目路径发生变化时，迁移其缓存记录到新路径。 */
	fun projectPathUpdate(project: JadxProject, newPath: Path) {
		if (project.getProjectPath() == newPath) {
			return
		}
		val key = projectToKey(project)
		val prevEntry = cacheMap.remove(key)
		if (prevEntry == null) {
			return
		}
		val newEntry = CacheEntry()
		newEntry.setProject(pathToString(newPath))
		newEntry.setCache(prevEntry.getCache())
		addEntry(newEntry)
	}

	/** 按最近使用时间倒序返回全部缓存记录。 */
	fun getCachesList(): List<CacheEntry> {
		val list = ArrayList(cacheMap.values)
		Collections.sort(list)
		return list
	}

	/** 删除某条缓存记录，并删除其磁盘目录。 */
	@Synchronized
	fun removeCacheEntry(entry: CacheEntry) {
		try {
			cacheMap.remove(entry.getProject())
			saveCaches(cacheMap)
			FileUtils.deleteDirIfExists(Paths.get(entry.getCache()))
		} catch (e: Exception) {
			LOG.error("Failed to remove cache entry: " + entry.getCache(), e)
		}
	}

	/** 相对缓存目录相对于项目路径解析；绝对路径或项目路径为空时直接返回。 */
	private fun resolveCacheDirStr(cacheDirStr: String, projectPath: Path?): Path {
		val path = Paths.get(cacheDirStr)
		if (path.isAbsolute || projectPath == null) {
			return path
		}
		return projectPath.resolveSibling(path)
	}

	/** 把缓存目录转换为显示/持久化用的字符串；配置为 `.` 时只返回目录名。 */
	fun buildCacheDirStr(dir: Path): String {
		if (settings.getCacheDir() == ".") {
			return dir.fileName.toString()
		}
		return pathToString(dir)
	}

	/** 根据设置构建新项目的缓存目录。 */
	private fun buildCacheDir(project: JadxProject): Path {
		val cacheDirValue = settings.getCacheDir()
		if (cacheDirValue == ".") {
			return buildLocalCacheDir(project)
		}
		val cacheBaseDir = if (cacheDirValue == null) JadxFiles.PROJECTS_CACHE_DIR else Paths.get(cacheDirValue)
		return cacheBaseDir.resolve(buildProjectUniqName(project))
	}

	/**
	 * 构建“项目本地”缓存目录（配置为 `.` 时）：
	 * 优先放在项目文件同级目录 `<项目名>.cache`，否则放在首个输入文件同级目录。
	 */
	private fun buildLocalCacheDir(project: JadxProject): Path {
		val projectPath = project.getProjectPath()
		if (projectPath != null) {
			return projectPath.resolveSibling(projectPath.fileName.toString() + ".cache")
		}
		val files = project.getFilePaths()
		if (files.isEmpty()) {
			throw JadxRuntimeException("Failed to build local cache dir")
		}
		val path = files.stream()
			.filter { p -> !p.fileName.toString().endsWith(".jadx.kts") }
			.findFirst()
			.orElseGet { files[0] }
		val name = CommonFileUtils.removeFileExtension(path.fileName.toString())
		return path.resolveSibling("$name.jadx.cache")
	}

	/**
	 * 校验并（必要时）更新项目缓存记录：
	 * 无记录则新建；路径一致且目录存在则直接返回；否则删除旧目录并更新记录。
	 */
	private fun verifyEntry(project: JadxProject, cacheDir: Path): Path {
		val cacheExists = Files.exists(cacheDir)
		val key = projectToKey(project)
		val entry = cacheMap[key]
		if (entry == null) {
			val newCacheDir = if (cacheExists) cacheDir else buildCacheDir(project)
			addEntry(key, newCacheDir)
			return newCacheDir
		}
		if (entry.getCache() == pathToString(cacheDir) && cacheExists) {
			// 路径相同且目录存在
			return cacheDir
		}
		// 删除旧缓存目录
		FileUtils.deleteDirIfExists(Paths.get(entry.getCache()))

		val newCacheDir = if (cacheExists) cacheDir else buildCacheDir(project)
		entry.setCache(pathToString(newCacheDir))
		entry.setTimestamp(System.currentTimeMillis())
		saveCaches(cacheMap)
		return newCacheDir
	}

	private fun addEntry(projectKey: String, cacheDir: Path) {
		val entry = CacheEntry()
		entry.setProject(projectKey)
		entry.setCache(pathToString(cacheDir))
		addEntry(entry)
	}

	private fun addEntry(entry: CacheEntry) {
		entry.setTimestamp(System.currentTimeMillis())
		cacheMap[entry.getProject()] = entry
		saveCaches(cacheMap)
	}

	/** 生成记录键：有项目文件用绝对路径，否则用 `tmp:` + 项目唯一名。 */
	private fun projectToKey(project: JadxProject): String {
		val projectPath = project.getProjectPath()
		if (projectPath != null) {
			return pathToString(projectPath)
		}
		return "tmp:" + buildProjectUniqName(project)
	}

	/** 从 `caches.json` 加载记录；文件不存在时从最近项目列表初始化。 */
	@Synchronized
	private fun loadCaches(): MutableMap<String, CacheEntry> {
		var list: List<CacheEntry>? = null
		if (Files.exists(JadxFiles.CACHES_LIST)) {
			try {
				Files.newBufferedReader(JadxFiles.CACHES_LIST).use { reader: BufferedReader ->
					list = GSON.fromJson<List<CacheEntry>>(reader, CACHES_TYPE)
				}
			} catch (e: Exception) {
				LOG.warn("Failed to load caches list", e)
			}
		} else {
			return initFromRecentProjects()
		}
		val loaded = list
		if (loaded == null || loaded.isEmpty()) {
			return HashMap()
		}
		val map = HashMap<String, CacheEntry>(loaded.size)
		for (entry in loaded) {
			map[entry.getProject()] = entry
		}
		return map
	}

	/** 把记录按时间倒序写回 `caches.json`。 */
	@Synchronized
	private fun saveCaches(map: Map<String, CacheEntry>) {
		val list = ArrayList(map.values)
		Collections.sort(list)
		val json = GSON.toJson(list, CACHES_TYPE)
		try {
			Files.writeString(
				JadxFiles.CACHES_LIST,
				json,
				StandardCharsets.UTF_8,
				StandardOpenOption.WRITE,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
			)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to write caches file", e)
		}
	}

	/**
	 * 从“最近项目”列表初始化缓存记录（用于首次迁移）。
	 * 递增的时间戳仅用于保持项目在列表中的原有顺序。
	 */
	private fun initFromRecentProjects(): MutableMap<String, CacheEntry> {
		try {
			val map = HashMap<String, CacheEntry>()
			var t = System.currentTimeMillis()
			for (project in settings.getRecentProjects()) {
				try {
					val data = JadxProject.loadProjectData(project)
					val cacheDir = data.getCacheDir()
					if (cacheDir == null) {
						// 无缓存目录，跳过
						continue
					}
					val cachePath = resolveCacheDirStr(cacheDir, project)
					if (!Files.isDirectory(cachePath)) {
						continue
					}
					val key = pathToString(project)
					val entry = CacheEntry()
					entry.setProject(key)
					entry.setCache(pathToString(cachePath))
					entry.setTimestamp(t++) // 保持项目顺序
					map[key] = entry
				} catch (e: Exception) {
					LOG.warn("Failed to load project file: {}", project, e)
				}
			}
			saveCaches(map)
			return map
		} catch (e: Exception) {
			LOG.warn("Failed to fill cache list from recent projects", e)
			return HashMap()
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CacheManager::class.java)

		private val GSON: Gson = GsonUtils.buildGson()

		private val CACHES_TYPE: Type = object : TypeToken<List<CacheEntry>>() {}.type

		/** 把路径规范化为绝对路径字符串，失败时抛出运行时异常。 */
		@JvmStatic
		fun pathToString(path: Path): String {
			try {
				return path.toAbsolutePath().normalize().toString()
			} catch (e: Exception) {
				throw JadxRuntimeException("Failed to expand path: $path", e)
			}
		}

		/** 生成项目唯一名：`项目名-输入哈希`。 */
		private fun buildProjectUniqName(project: JadxProject): String = project.getName() + '-' + project.getInputsHash()
	}
}
