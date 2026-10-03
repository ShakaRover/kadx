package jadx.gui.utils.plugins

import jadx.api.JadxDecompiler
import jadx.cli.plugins.JadxFilesGetter
import jadx.core.plugins.AppContext
import jadx.core.plugins.PluginContext
import jadx.gui.ui.MainWindow
import jadx.plugins.tools.JadxExternalPluginsLoader
import java.util.SortedSet

/**
 * 收集全部插件。
 *
 * **做什么**：
 * - 若包装器里的 [JadxDecompiler] 已初始化，直接返回其已解析的插件上下文；
 * - 否则在临时上下文中加载并初始化插件（不带 GUI 上下文），
 *   并返回一个可在关闭时卸载这些临时插件的 [CloseablePlugins]。
 *
 * **注意**：`JadxArgs.filesGetter` 是 Kotlin 属性，这里用属性赋值而非
 * `setFilesGetter()`（Kotlin 不允许显式调用 Kotlin 属性的 getter/setter）。
 */
class CollectPlugins(private val mainWindow: MainWindow) {

	fun build(): CloseablePlugins {
		val currentDecompiler: JadxDecompiler? = mainWindow.getWrapper().currentDecompiler
		if (currentDecompiler != null) {
			val plugins: SortedSet<PluginContext> = currentDecompiler.getPluginManager().resolvedPluginContexts
			return CloseablePlugins(ArrayList(plugins), null)
		}
		// 在临时上下文中收集并初始化插件
		val jadxArgs = mainWindow.getSettings().toJadxArgs()
		jadxArgs.filesGetter = JadxFilesGetter.INSTANCE
		JadxDecompiler(jadxArgs).use { decompiler ->
			val pluginManager = decompiler.getPluginManager()
			pluginManager.registerAddPluginListener { pluginContext ->
				val appContext = AppContext()
				appContext.setGuiContext(null) // 加载临时插件时不带 UI 上下文
				appContext.setFilesGetter(jadxArgs.filesGetter)
				pluginContext.setAppContext(appContext)
			}
			pluginManager.load(JadxExternalPluginsLoader())
			val allPlugins: SortedSet<PluginContext> = pluginManager.allPluginContexts
			pluginManager.init(allPlugins)
			val closeable = Runnable { pluginManager.unload(allPlugins) }
			return CloseablePlugins(ArrayList(allPlugins), closeable)
		}
	}
}
