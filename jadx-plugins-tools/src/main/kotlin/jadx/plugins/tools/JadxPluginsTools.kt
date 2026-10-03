package jadx.plugins.tools

import jadx.api.plugins.JadxPlugin
import jadx.core.Jadx.getVersion
import jadx.core.plugins.versions.VerifyRequiredVersion
import jadx.core.utils.StringUtils.Companion.notBlank
import jadx.core.utils.Utils.getOrElse
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils.deleteDir
import jadx.core.utils.files.FileUtils.deleteDirIfExists
import jadx.core.utils.files.FileUtils.listFiles
import jadx.plugins.tools.data.JadxInstalledPlugins
import jadx.plugins.tools.data.JadxPluginMetadata
import jadx.plugins.tools.data.JadxPluginUpdate
import jadx.plugins.tools.resolvers.ResolversRegistry.getResolver
import jadx.plugins.tools.utils.PluginFiles.DROPINS_DIR
import jadx.plugins.tools.utils.PluginFiles.INSTALLED_DIR
import jadx.plugins.tools.utils.PluginFiles.PLUGINS_JSON
import jadx.plugins.tools.utils.PluginUtils.downloadFile
import jadx.zip.IZipEntry
import jadx.zip.ZipReader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files.copy
import java.nio.file.Files.createTempDirectory
import java.nio.file.Files.createTempFile
import java.nio.file.Files.deleteIfExists
import java.nio.file.Files.isDirectory
import java.nio.file.Files.isRegularFile
import java.nio.file.Files.newBufferedReader
import java.nio.file.Files.newBufferedWriter
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Paths.get as getPath

class JadxPluginsTools private constructor() {
	companion object {
		val instance = JadxPluginsTools()

		private val LOG = LoggerFactory.getLogger(JadxPluginsTools::class.java)
	}

	fun install(locationId: String): JadxPluginMetadata {
		val resolver = getResolver(locationId)
		val fetchVersions: () -> List<JadxPluginMetadata> = if (resolver.hasVersion(locationId)) {
			{
				val version = resolver.resolve(locationId)
					?: throw JadxRuntimeException("Failed to resolve plugin location: $locationId")
				listOf(version)
			}
		} else {
			{ resolver.resolveVersions(locationId, 1, 10) }
		}

		val versionsMetadata = try {
			fetchVersions()
		} catch (e: Exception) {
			throw JadxRuntimeException("Plugin info fetch failed, locationId: $locationId", e)
		}
		if (versionsMetadata.isEmpty()) {
			throw JadxRuntimeException("Plugin release not found, locationId: $locationId")
		}

		val verifyRequiredVersion = VerifyRequiredVersion()
		val rejectedVersions = ArrayList<String>()
		for (pluginMetadata in versionsMetadata) {
			fillMetadata(pluginMetadata)
			if (verifyRequiredVersion.isCompatible(pluginMetadata.requiredJadxVersion)) {
				install(pluginMetadata)
				return pluginMetadata
			}
			val pluginVersion = getOrElse(pluginMetadata.version, "unknown")
			rejectedVersions.add(" version '$pluginVersion' not compatible, require: ${pluginMetadata.requiredJadxVersion}")
		}
		throw JadxRuntimeException(
			"Can't find compatible version to install" +
				", current jadx version: ${verifyRequiredVersion.getJadxVersion()}" +
				"\nrejected plugin versions:\n" +
				rejectedVersions.joinToString("\n"),
		)
	}

	fun resolveMetadata(locationId: String): JadxPluginMetadata {
		val resolver = getResolver(locationId)
		val pluginMetadata = resolver.resolve(locationId)
			?: throw RuntimeException("Failed to resolve locationId: $locationId")
		fillMetadata(pluginMetadata)
		return pluginMetadata
	}

	fun getVersionsByLocation(locationId: String, page: Int, perPage: Int): List<JadxPluginMetadata> {
		val resolver = getResolver(locationId)
		val list = resolver.resolveVersions(locationId, page, perPage)
		for (pluginMetadata in list) {
			fillMetadata(pluginMetadata)
		}
		return list
	}

	fun updateAll(): List<JadxPluginUpdate> {
		val plugins = loadPluginsJson()
		val size = plugins.installed.size
		val updates = ArrayList<JadxPluginUpdate>(size)
		val newList = ArrayList<JadxPluginMetadata>(size)
		for (plugin in plugins.installed) {
			var newVersion: JadxPluginMetadata? = null
			try {
				newVersion = update(plugin)
			} catch (e: Exception) {
				LOG.warn("Failed to update plugin: {}", plugin.pluginId, e)
			}
			if (newVersion != null) {
				updates.add(JadxPluginUpdate(plugin, newVersion))
				newList.add(newVersion)
			} else {
				newList.add(plugin)
			}
		}
		if (updates.isNotEmpty()) {
			plugins.updated = System.currentTimeMillis()
			plugins.installed = newList
			savePluginsJson(plugins)
		}
		return updates
	}

	fun update(pluginId: String): JadxPluginUpdate? {
		val plugins = loadPluginsJson()
		val plugin = plugins.installed.firstOrNull { it.pluginId == pluginId }
			?: throw RuntimeException("Plugin not found: $pluginId")

		val newVersion = update(plugin)
		if (newVersion == null) {
			return null
		}
		plugins.updated = System.currentTimeMillis()
		plugins.installed.remove(plugin)
		plugins.installed.add(newVersion)
		savePluginsJson(plugins)
		return JadxPluginUpdate(plugin, newVersion)
	}

	fun uninstall(pluginId: String): Boolean {
		val plugins = loadPluginsJson()
		val found = plugins.installed.firstOrNull { it.pluginId == pluginId } ?: return false
		deletePlugin(found)
		plugins.installed.remove(found)
		savePluginsJson(plugins)
		return true
	}

	fun getInstalled(): List<JadxPluginMetadata> = loadPluginsJson().installed

	fun getAllPluginsInfo(): List<jadx.api.plugins.JadxPluginInfo> {
		val pluginsLoader = JadxExternalPluginsLoader()
		try {
			return pluginsLoader.load().map { it.getPluginInfo() }
		} finally {
			pluginsLoader.close()
		}
	}

	fun getEnabledPluginPaths(): List<Path> {
		val list = ArrayList<Path>()
		for (pluginMetadata in loadPluginsJson().installed) {
			if (pluginMetadata.disabled) {
				continue
			}
			list.add(INSTALLED_DIR.resolve(checkNotNull(pluginMetadata.path)))
		}
		list.addAll(listFiles(DROPINS_DIR))
		return list
	}

	fun changeDisabledStatus(pluginId: String, disabled: Boolean): Boolean {
		val data = loadPluginsJson()
		val plugin = data.installed.firstOrNull { it.pluginId == pluginId }
			?: throw RuntimeException("Plugin not found: $pluginId")
		if (plugin.disabled == disabled) {
			return false
		}
		plugin.disabled = disabled
		data.updated = System.currentTimeMillis()
		savePluginsJson(data)
		return true
	}

	private fun update(plugin: JadxPluginMetadata): JadxPluginMetadata? {
		val resolver = getResolver(checkNotNull(plugin.locationId))
		if (!resolver.isUpdateSupported) {
			return null
		}
		val update = resolver.resolve(checkNotNull(plugin.locationId)) ?: return null
		if (update.version == plugin.version) {
			return null
		}
		fillMetadata(update)
		install(update)
		return update
	}

	private fun install(metadata: JadxPluginMetadata) {
		val reqVersionStr = metadata.requiredJadxVersion
		if (!VerifyRequiredVersion.isJadxCompatible(reqVersionStr)) {
			throw JadxRuntimeException(
				"Can't install plugin, required version: \"$reqVersionStr\"" +
					" is not compatible with current jadx version: ${getVersion()}",
			)
		}
		uninstall(checkNotNull(metadata.pluginId))

		val version = metadata.version
		val pluginBaseName = "${metadata.pluginId}${if (notBlank(version)) "-$version" else ""}"
		val pluginPathStr = checkNotNull(metadata.path)
		val pluginPath = getPath(pluginPathStr)
		if (pluginPathStr.endsWith(".jar")) {
			val pluginJar = INSTALLED_DIR.resolve("$pluginBaseName.jar")
			copyJar(pluginPath, pluginJar)
			metadata.path = INSTALLED_DIR.relativize(pluginJar).toString()
		} else if (isDirectory(pluginPath)) {
			val pluginDir = INSTALLED_DIR.resolve(pluginBaseName)
			try {
				deleteDirIfExists(pluginDir)
				org.apache.commons.io.FileUtils.moveDirectory(pluginPath.toFile(), pluginDir.toFile())
			} catch (e: IOException) {
				throw JadxRuntimeException("Failed to install plugin: $pluginBaseName", e)
			}
			metadata.path = INSTALLED_DIR.relativize(pluginDir).toString()
		} else {
			throw JadxRuntimeException("Unexpected plugin path type: $pluginPathStr")
		}

		val plugins = loadPluginsJson()
		plugins.installed.add(metadata)
		plugins.updated = System.currentTimeMillis()
		savePluginsJson(plugins)
	}

	private fun fillMetadata(metadata: JadxPluginMetadata) {
		try {
			var pluginPath = checkNotNull(metadata.path)
			if (needDownload(pluginPath)) {
				val ext = jadx.api.plugins.utils.CommonFileUtils.getFileExtension(pluginPath)
				val tmpJar = createTempFile(checkNotNull(metadata.name), "plugin.$ext")
				downloadFile(pluginPath, tmpJar)
				pluginPath = tmpJar.toAbsolutePath().toString()
			}
			if (pluginPath.endsWith(".zip")) {
				val tmpDir = createTempDirectory(checkNotNull(metadata.name))
				unzip(getPath(pluginPath), tmpDir)
				pluginPath = tmpDir.toAbsolutePath().toString()
			}
			metadata.path = pluginPath
			fillMetadataFromPath(metadata, getPath(pluginPath))
		} catch (e: Exception) {
			throw RuntimeException("Failed to fill plugin metadata, plugin: ${metadata.pluginId}", e)
		}
	}

	private fun fillMetadataFromPath(metadata: JadxPluginMetadata, pluginPath: Path) {
		val loader = JadxExternalPluginsLoader()
		try {
			val jadxPlugin = loader.loadFromPath(pluginPath)
			val pluginInfo = jadxPlugin.getPluginInfo()
			metadata.pluginId = pluginInfo.getPluginId()
			metadata.name = pluginInfo.getName()
			metadata.description = pluginInfo.getDescription()
			metadata.homepage = pluginInfo.getHomepage()
			metadata.requiredJadxVersion = pluginInfo.getRequiredJadxVersion()
		} catch (e: NoSuchMethodError) {
			throw RuntimeException("Looks like plugin uses unknown API, try to update jadx version", e)
		} finally {
			loader.close()
		}
	}

	private fun needDownload(jar: String): Boolean = jar.startsWith("https://") || jar.startsWith("http://")

	private fun copyJar(sourceJar: Path, destJar: Path) {
		try {
			copy(sourceJar, destJar, REPLACE_EXISTING)
		} catch (e: Exception) {
			throw RuntimeException("Failed to copy plugin jar: $sourceJar to: $destJar", e)
		}
	}

	private fun deletePlugin(plugin: JadxPluginMetadata) {
		try {
			val pluginPath = INSTALLED_DIR.resolve(checkNotNull(plugin.path))
			if (isDirectory(pluginPath)) {
				deleteDir(pluginPath)
			} else {
				deleteIfExists(pluginPath)
			}
		} catch (e: IOException) {
			// ignore
		}
	}

	private fun loadPluginsJson(): JadxInstalledPlugins {
		if (!isRegularFile(PLUGINS_JSON)) {
			val plugins = JadxInstalledPlugins()
			plugins.version = 1
			return plugins
		}
		try {
			val reader = newBufferedReader(PLUGINS_JSON, UTF_8)
			reader.use {
				val data = jadx.core.utils.GsonUtils.buildGson().fromJson(it, JadxInstalledPlugins::class.java)
				upgradePluginsData(data)
				return data
			}
		} catch (e: Exception) {
			throw RuntimeException("Failed to read file: $PLUGINS_JSON")
		}
	}

	private fun savePluginsJson(data: JadxInstalledPlugins) {
		if (data.installed.isEmpty()) {
			try {
				deleteIfExists(PLUGINS_JSON)
			} catch (e: Exception) {
				throw RuntimeException("Failed to remove file: $PLUGINS_JSON", e)
			}
			return
		}
		data.installed.sort()
		try {
			val writer = newBufferedWriter(PLUGINS_JSON, UTF_8)
			writer.use { jadx.core.utils.GsonUtils.buildGson().toJson(data, it) }
		} catch (e: Exception) {
			throw RuntimeException("Error saving file: $PLUGINS_JSON", e)
		}
	}

	private fun upgradePluginsData(data: JadxInstalledPlugins) {
		if (data.version == 0) {
			data.version = 1
		}
	}

	private fun unzip(zipFile: Path, outDir: Path) {
		val zipReader = ZipReader()
		try {
			val content = zipReader.open(zipFile.toFile())
			content.use {
				for (entry in it.entries) {
					val entryFile = outDir.resolve(entry.getName())
					copy(entry.getInputStream(), entryFile, REPLACE_EXISTING)
				}
			}
		} catch (e: IOException) {
			throw JadxRuntimeException("Failed to unzip file: $zipFile", e)
		}
	}
}
