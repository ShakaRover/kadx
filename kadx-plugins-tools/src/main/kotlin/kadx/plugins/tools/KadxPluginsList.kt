package kadx.plugins.tools

import com.google.gson.reflect.TypeToken
import kadx.commons.app.KadxCommonEnv
import kadx.core.utils.GsonUtils.buildGson
import kadx.core.utils.exceptions.KadxRuntimeException
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

		/**
		 * 插件市场（kadx-plugins-list）位置，语法与插件 locationId 一致：`github:<owner>:<repo>`。
		 *
		 * 未设置时市场功能整体关闭（不联网、不报错）—— 因为 kadx 目前没有自己的插件列表仓，
		 * 而上游 `jadx-decompiler/jadx-plugins-list` 里的插件是按 jadx 的包名与
		 * `META-INF/services/jadx.api.plugins.JadxPlugin` 描述符构建的，kadx 通过
		 * `ServiceLoader.load(KadxPlugin::class.java)` 发现不了，装了也不会生效。
		 */
		const val LOCATION_ENV = "KADX_PLUGINS_LIST_LOCATION"

		private val LOG = LoggerFactory.getLogger(KadxPluginsList::class.java)

		/** 已配置的市场位置；未配置或格式非法时为 null。 */
		val location: LocationInfo? = parseLocation(KadxCommonEnv.get(LOCATION_ENV, null))

		/** 市场是否可用（是否配置了合法位置）。 */
		val isEnabled: Boolean get() = location != null

		private fun parseLocation(raw: String?): LocationInfo? {
			if (raw == null) {
				return null
			}
			val parts = raw.trim().split(":")
			if (parts.size != 3 || parts[0] != "github" || parts[1].isEmpty() || parts[2].isEmpty()) {
				LOG.warn(
					"Ignore invalid {} value: '{}' (expected 'github:<owner>:<repo>'), marketplace disabled",
					LOCATION_ENV,
					raw,
				)
				return null
			}
			return LocationInfo(parts[1], parts[2], "list")
		}
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
		val listLocation = location
			?: throw KadxRuntimeException(
				"Plugins marketplace location is not configured, set '$LOCATION_ENV' to 'github:<owner>:<repo>'",
			)
		LOG.debug("Fetching latest plugins-list release info")
		val release = GithubTools.fetchRelease(listLocation)
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
