package jadx.plugins.tools

import com.google.gson.reflect.TypeToken
import jadx.core.utils.GsonUtils.buildGson
import jadx.core.utils.files.FileUtils.readFile
import jadx.core.utils.files.FileUtils.writeFile
import jadx.plugins.tools.data.JadxPluginListCache
import jadx.plugins.tools.data.JadxPluginListEntry
import jadx.plugins.tools.resolvers.github.GithubTools
import jadx.plugins.tools.resolvers.github.LocationInfo
import jadx.plugins.tools.resolvers.github.data.Release
import jadx.plugins.tools.utils.PluginFiles.PLUGINS_LIST_CACHE
import jadx.plugins.tools.utils.PluginUtils.downloadFile
import jadx.zip.ZipReader
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.file.Files.createTempFile
import java.nio.file.Files.deleteIfExists
import java.nio.file.Files.isRegularFile
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class JadxPluginsList private constructor() {
	companion object {
		@JvmStatic
		fun getInstance(): JadxPluginsList = instance

		private val instance = JadxPluginsList()
		private val LOG = LoggerFactory.getLogger(JadxPluginsList::class.java)
	}

	private val listType: Type = object : TypeToken<List<JadxPluginListEntry>>() {}.type
	private val cacheType: Type = object : TypeToken<JadxPluginListCache>() {}.type

	@Volatile
	private var loadedList: JadxPluginListCache? = null

	fun get(consumer: (List<JadxPluginListEntry>) -> Unit) {
		synchronized(this) {
			val list = loadedList
			if (list != null) {
				consumer(list.list!!)
				return
			}
			var listCache = loadCache()
			if (listCache != null) {
				consumer(listCache.list!!)
				loadedList = listCache
			}
			val release = fetchLatestRelease()
			if (listCache == null || listCache.version != release.name) {
				val updatedList = fetchBundle(release)
				saveCache(updatedList)
				consumer(updatedList.list!!)
				loadedList = updatedList
			}
		}
	}

	fun get(): List<JadxPluginListEntry> {
		val holder = AtomicReference<List<JadxPluginListEntry>>()
		get(holder::set)
		return holder.get()!!
	}

	private fun loadCache(): JadxPluginListCache? {
		if (!isRegularFile(PLUGINS_LIST_CACHE)) {
			return null
		}
		try {
			val jsonStr = readFile(PLUGINS_LIST_CACHE)
			return buildGson().fromJson(jsonStr, cacheType)
		} catch (e: Exception) {
			return null
		}
	}

	private fun saveCache(listCache: JadxPluginListCache) {
		try {
			val jsonStr = buildGson().toJson(listCache, cacheType)
			writeFile(PLUGINS_LIST_CACHE, jsonStr)
		} catch (e: Exception) {
			throw RuntimeException("Error saving file: $PLUGINS_LIST_CACHE", e)
		}
	}

	private fun fetchLatestRelease(): Release {
		LOG.debug("Fetching latest plugins-list release info")
		val pluginsList = LocationInfo("jadx-decompiler", "jadx-plugins-list", "list")
		val release = GithubTools.fetchRelease(pluginsList)
		if (release.assets.isNullOrEmpty()) {
			throw RuntimeException("Release don't have assets")
		}
		return release
	}

	private fun fetchBundle(release: Release): JadxPluginListCache {
		LOG.debug("Fetching plugins-list bundle: {}", release.name)
		try {
			val listAsset = release.assets!![0]
			val tmpListFile = createTempFile("plugins-list", ".zip")
			try {
				downloadFile(listAsset.downloadUrl!!, tmpListFile)
				val listCache = JadxPluginListCache()
				listCache.version = release.name
				listCache.list = loadListBundle(tmpListFile)
				return listCache
			} finally {
				deleteIfExists(tmpListFile)
			}
		} catch (e: Exception) {
			throw RuntimeException("Failed to load plugin-list bundle for release:${release.name}", e)
		}
	}

	private fun loadListBundle(tmpListFile: Path): List<JadxPluginListEntry> {
		val gson = buildGson()
		val entries = ArrayList<JadxPluginListEntry>()
		ZipReader().visitEntries(tmpListFile.toFile()) { entry ->
			if (entry.getName().endsWith(".json")) {
				try {
					val reader = InputStreamReader(entry.getInputStream())
					reader.use { entries.addAll(gson.fromJson(it, listType)) }
				} catch (e: Exception) {
					throw RuntimeException("Failed to read plugins list entry: ${entry.getName()}")
				}
			}
			null
		}
		return entries
	}
}
