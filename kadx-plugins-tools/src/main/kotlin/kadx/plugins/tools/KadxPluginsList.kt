package kadx.plugins.tools

import com.google.gson.reflect.TypeToken
import kadx.core.utils.GsonUtils.buildGson
import kadx.core.utils.files.FileUtils.readFile
import kadx.core.utils.files.FileUtils.writeFile
import kadx.plugins.tools.data.KadxPluginListCache
import kadx.plugins.tools.data.KadxPluginListEntry
import kadx.plugins.tools.resolvers.github.GithubTools
import kadx.plugins.tools.resolvers.github.LocationInfo
import kadx.plugins.tools.resolvers.github.data.Release
import kadx.plugins.tools.utils.PluginFiles.PLUGINS_LIST_CACHE
import kadx.plugins.tools.utils.PluginUtils.downloadFile
import kadx.zip.ZipReader
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.file.Files.createTempFile
import java.nio.file.Files.deleteIfExists
import java.nio.file.Files.isRegularFile
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class KadxPluginsList private constructor() {
	companion object {
		val instance = KadxPluginsList()

		private val LOG = LoggerFactory.getLogger(KadxPluginsList::class.java)
	}

	private val listType: Type = object : TypeToken<List<KadxPluginListEntry>>() {}.type
	private val cacheType: Type = object : TypeToken<KadxPluginListCache>() {}.type

	@Volatile
	private var loadedList: KadxPluginListCache? = null

	fun get(consumer: (List<KadxPluginListEntry>) -> Unit) {
		synchronized(this) {
			val list = loadedList
			if (list != null) {
				consumer(checkNotNull(list.list))
				return
			}
			var listCache = loadCache()
			if (listCache != null) {
				consumer(checkNotNull(listCache.list))
				loadedList = listCache
			}
			val release = fetchLatestRelease()
			if (listCache == null || listCache.version != release.name) {
				val updatedList = fetchBundle(release)
				saveCache(updatedList)
				consumer(checkNotNull(updatedList.list))
				loadedList = updatedList
			}
		}
	}

	fun get(): List<KadxPluginListEntry> {
		val holder = AtomicReference<List<KadxPluginListEntry>>()
		get(holder::set)
		return checkNotNull(holder.get())
	}

	private fun loadCache(): KadxPluginListCache? {
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

	private fun saveCache(listCache: KadxPluginListCache) {
		try {
			val jsonStr = buildGson().toJson(listCache, cacheType)
			writeFile(PLUGINS_LIST_CACHE, jsonStr)
		} catch (e: Exception) {
			throw RuntimeException("Error saving file: $PLUGINS_LIST_CACHE", e)
		}
	}

	private fun fetchLatestRelease(): Release {
		LOG.debug("Fetching latest plugins-list release info")
		val pluginsList = LocationInfo("kadx-decompiler", "kadx-plugins-list", "list")
		val release = GithubTools.fetchRelease(pluginsList)
		if (release.assets.isNullOrEmpty()) {
			throw RuntimeException("Release don't have assets")
		}
		return release
	}

	private fun fetchBundle(release: Release): KadxPluginListCache {
		LOG.debug("Fetching plugins-list bundle: {}", release.name)
		try {
			val listAsset = checkNotNull(release.assets)[0]
			val tmpListFile = createTempFile("plugins-list", ".zip")
			try {
				downloadFile(checkNotNull(listAsset.downloadUrl), tmpListFile)
				val listCache = KadxPluginListCache()
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

	private fun loadListBundle(tmpListFile: Path): List<KadxPluginListEntry> {
		val gson = buildGson()
		val entries = ArrayList<KadxPluginListEntry>()
		ZipReader().visitEntries(tmpListFile.toFile()) { entry ->
			if (entry.name.endsWith(".json")) {
				try {
					val reader = InputStreamReader(entry.inputStream)
					reader.use { entries.addAll(gson.fromJson(it, listType)) }
				} catch (e: Exception) {
					throw RuntimeException("Failed to read plugins list entry: ${entry.name}")
				}
			}
			null
		}
		return entries
	}
}
