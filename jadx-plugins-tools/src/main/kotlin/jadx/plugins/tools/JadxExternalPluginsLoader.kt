package jadx.plugins.tools

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.core.utils.Utils.first
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils.hasExtension
import jadx.core.utils.files.FileUtils.listFiles
import org.slf4j.LoggerFactory
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files.isDirectory
import java.nio.file.Files.isRegularFile
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.stream.Collectors

class JadxExternalPluginsLoader : JadxPluginLoader {
	companion object {
		private val LOG = LoggerFactory.getLogger(JadxExternalPluginsLoader::class.java)
		const val JADX_PLUGIN_CLASSLOADER_PREFIX = "jadx-plugin:"

		private fun toURL(pluginPath: Path): URL = try {
			pluginPath.toUri().toURL()
		} catch (e: MalformedURLException) {
			throw RuntimeException(e)
		}

		private fun thisClassLoader(): ClassLoader = JadxExternalPluginsLoader::class.java.classLoader
	}

	private val classLoaders = ArrayList<URLClassLoader>()

	override fun load(): List<JadxPlugin> {
		close()
		val start = System.currentTimeMillis()
		val map = HashMap<String, JadxPlugin>()
		loadFromClsLoader(map, thisClassLoader())
		loadInstalledPlugins(map)

		val list = ArrayList<JadxPlugin>(map.size)
		list.addAll(map.values)
		list.sortBy { it.javaClass.simpleName }
		if (LOG.isDebugEnabled) {
			LOG.debug("Collected {} plugins in {}ms", list.size, System.currentTimeMillis() - start)
		}
		return list
	}

	fun loadFromPath(pluginPath: Path): JadxPlugin {
		val map = HashMap<String, JadxPlugin>()
		loadFromPath(map, pluginPath)
		val loaded = map.size
		if (loaded == 0) {
			throw JadxRuntimeException("No plugin found in jar: $pluginPath")
		}
		if (loaded > 1) {
			val plugins = map.values.joinToString(", ") { it.pluginInfo.pluginId }
			throw JadxRuntimeException("Expect only one plugin per jar: $pluginPath, but found: $loaded - $plugins")
		}
		return first(map.values)!!
	}

	private fun loadFromClsLoader(map: MutableMap<String, JadxPlugin>, classLoader: ClassLoader) {
		val serviceLoader = ServiceLoader.load(JadxPlugin::class.java, classLoader)
		val providers = serviceLoader.stream().collect(Collectors.toList())
		for (provider in providers) {
			val pluginClass = provider.type()
			val clsName = pluginClass.name
			if (!map.containsKey(clsName) && pluginClass.classLoader == classLoader) {
				map[clsName] = provider.get()
			}
		}
	}

	private fun loadInstalledPlugins(map: MutableMap<String, JadxPlugin>) {
		val paths = JadxPluginsTools.getInstance().getEnabledPluginPaths()
		for (pluginPath in paths) {
			loadFromPath(map, pluginPath)
		}
	}

	private fun loadFromPath(map: MutableMap<String, JadxPlugin>, pluginPath: Path) {
		try {
			val urls = if (isDirectory(pluginPath)) {
				listFiles(pluginPath) { hasExtension(it, ".jar") }
					.map { toURL(it) }
					.toTypedArray()
			} else if (isRegularFile(pluginPath)) {
				if (hasExtension(pluginPath, ".jar")) {
					arrayOf(toURL(pluginPath))
				} else {
					throw JadxRuntimeException("Unexpected plugin file format")
				}
			} else {
				throw JadxRuntimeException("Plugin file not found")
			}
			if (urls.isEmpty()) {
				throw JadxRuntimeException("No jar files found in plugin directory")
			}
			val clsLoaderName = JADX_PLUGIN_CLASSLOADER_PREFIX + pluginPath.fileName
			val pluginClsLoader = URLClassLoader(clsLoaderName, urls, thisClassLoader())
			classLoaders.add(pluginClsLoader)
			loadFromClsLoader(map, pluginClsLoader)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to load plugins from: $pluginPath", e)
		}
	}

	override fun close() {
		try {
			for (classLoader in classLoaders) {
				try {
					classLoader.close()
				} catch (e: Exception) {
					// ignore
				}
			}
		} finally {
			classLoaders.clear()
		}
	}
}
