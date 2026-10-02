package jadx.core.plugins

import jadx.api.JadxDecompiler
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.data.IJadxPlugins
import jadx.api.plugins.data.JadxPluginRuntimeData
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * [IJadxPlugins] 实现：从 [JadxPluginManager] 的已解析插件集合中查询插件。
 *
 * **做什么**：按插件 id、按提供的功能 id（`provides`）、或按插件类，在已解析
 * （resolved）的插件中查找；找不到时抛出 [JadxRuntimeException]。
 */
class JadxPluginsData(
	@Suppress("unused")
	private val decompiler: JadxDecompiler,
	private val pluginManager: JadxPluginManager,
) : IJadxPlugins {

	override fun getById(pluginId: String): JadxPluginRuntimeData = pluginManager.getResolvedPluginContexts()
		.stream()
		.filter { p -> p.getPluginId() == pluginId }
		.findFirst()
		.orElseThrow { JadxRuntimeException("Plugin with id '$pluginId' not found") }

	override fun getProviding(provideId: String): JadxPluginRuntimeData = pluginManager.getResolvedPluginContexts()
		.stream()
		.filter { p -> p.getPluginInfo().getProvides() == provideId }
		.findFirst()
		.orElseThrow { JadxRuntimeException("Plugin providing '$provideId' not found") }

	@Suppress("UNCHECKED_CAST")
	override fun <P : JadxPlugin> getInstance(pluginCls: Class<P>): P = pluginManager.getResolvedPluginContexts()
		.stream()
		.filter { p -> p.getPluginInstance().javaClass == pluginCls }
		.map { p -> p.getPluginInstance() as P }
		.findFirst()
		.orElseThrow { JadxRuntimeException("Plugin class '$pluginCls' not found") }
}
