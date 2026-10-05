package kadx.core.plugins

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.data.IKadxPlugins
import kadx.api.plugins.data.KadxPluginRuntimeData
import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * [IKadxPlugins] 实现：从 [KadxPluginManager] 的已解析插件集合中查询插件。
 *
 * **做什么**：按插件 id、按提供的功能 id（`provides`）、或按插件类，在已解析
 * （resolved）的插件中查找；找不到时抛出 [KadxRuntimeException]。
 */
class KadxPluginsData(private val pluginManager: KadxPluginManager) : IKadxPlugins {

	override fun getById(pluginId: String): KadxPluginRuntimeData = pluginManager.resolvedPlugins
		.firstOrNull { p -> p.pluginId == pluginId }
		?.pluginContext
		?: throw KadxRuntimeException("Plugin with id '$pluginId' not found")

	override fun getProviding(provideId: String): KadxPluginRuntimeData = pluginManager.resolvedPlugins
		.firstOrNull { p -> p.pluginInfo.getProvides() == provideId }
		?.pluginContext
		?: throw KadxRuntimeException("Plugin providing '$provideId' not found")

	@Suppress("UNCHECKED_CAST")
	override fun <P : KadxPlugin> getInstance(pluginCls: Class<P>): P = pluginManager.resolvedPlugins
		.firstOrNull { p -> p.pluginInstance.javaClass == pluginCls }
		?.let { p -> p.pluginInstance as P }
		?: throw KadxRuntimeException("Plugin class '$pluginCls' not found")
}
