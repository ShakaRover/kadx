package kadx.plugins.tools

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.loader.KadxPluginLoader
import kadx.core.utils.Utils.first
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.files.FileUtils.hasExtension
import kadx.core.utils.files.FileUtils.listFiles
import org.slf4j.LoggerFactory
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files.isDirectory
import java.nio.file.Files.isRegularFile
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.function.Predicate
import kotlin.streams.toList

class KadxExternalPluginsLoader @JvmOverloads constructor(
	private val pluginClassFilter: Predicate<Class<*>> = Predicate { true },
) : KadxPluginLoader {
	companion object {
		private val LOG = LoggerFactory.getLogger(KadxExternalPluginsLoader::class.java)
		const val KADX_PLUGIN_CLASSLOADER_PREFIX = "kadx-plugin:"

		private fun toURL(pluginPath: Path): URL = try {
			pluginPath.toUri().toURL()
		} catch (e: MalformedURLException) {
			throw RuntimeException(e)
		}

		private fun thisClassLoader(): ClassLoader = KadxExternalPluginsLoader::class.java.classLoader
	}

	private val classLoaders = ArrayList<URLClassLoader>()

	override fun load(): List<KadxPlugin> {
		close()
		val start = System.currentTimeMillis()
		val map = HashMap<String, KadxPlugin>()
		loadFromClsLoader(map, thisClassLoader())
		loadInstalledPlugins(map)

		val list = ArrayList<KadxPlugin>(map.size)
		list.addAll(map.values)
		list.sortBy { it.javaClass.simpleName }
		if (LOG.isDebugEnabled) {
			LOG.debug("Collected {} plugins in {}ms", list.size, System.currentTimeMillis() - start)
		}
		return list
	}

	fun loadFromPath(pluginPath: Path): KadxPlugin {
		val map = HashMap<String, KadxPlugin>()
		loadFromPath(map, pluginPath)
		val loaded = map.size
		if (loaded > 1) {
			val plugins = map.values.joinToString(", ") { it.getPluginInfo().getPluginId() }
			throw KadxRuntimeException("Expect only one plugin per jar: $pluginPath, but found: $loaded - $plugins")
		}
		val plugin = first(map.values)
		if (plugin == null) {
			throw KadxRuntimeException("No plugin found in jar: $pluginPath")
		}
		return plugin
	}

	private fun loadFromClsLoader(map: MutableMap<String, KadxPlugin>, classLoader: ClassLoader) {
		val serviceLoader = ServiceLoader.load(KadxPlugin::class.java, classLoader)
		val providers = serviceLoader.stream().toList()
		for (provider in providers) {
			val pluginClass = provider.type()
			val clsName = pluginClass.name
			if (!map.containsKey(clsName) &&
				pluginClass.classLoader == classLoader &&
				pluginClassFilter.test(pluginClass)
			) {
				map[clsName] = provider.get()
			}
		}
	}

	private fun loadInstalledPlugins(map: MutableMap<String, KadxPlugin>) {
		val paths = KadxPluginsTools.instance.getEnabledPluginPaths()
		for (pluginPath in paths) {
			loadFromPath(map, pluginPath)
		}
	}

	private fun loadFromPath(map: MutableMap<String, KadxPlugin>, pluginPath: Path) {
		try {
			val urls = if (isDirectory(pluginPath)) {
				listFiles(pluginPath) { hasExtension(it, ".jar") }
					.map { toURL(it) }
					.toTypedArray()
			} else if (isRegularFile(pluginPath)) {
				if (hasExtension(pluginPath, ".jar")) {
					arrayOf(toURL(pluginPath))
				} else {
					throw KadxRuntimeException("Unexpected plugin file format")
				}
			} else {
				throw KadxRuntimeException("Plugin file not found")
			}
			if (urls.isEmpty()) {
				throw KadxRuntimeException("No jar files found in plugin directory")
			}
			val clsLoaderName = KADX_PLUGIN_CLASSLOADER_PREFIX + pluginPath.fileName
			val pluginClsLoader = URLClassLoader(clsLoaderName, urls, thisClassLoader())
			classLoaders.add(pluginClsLoader)
			loadFromClsLoader(map, pluginClsLoader)
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to load plugins from: $pluginPath", e)
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
