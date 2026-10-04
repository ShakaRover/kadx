package jadx.gui.utils.plugins

import jadx.api.JadxDecompiler
import jadx.cli.plugins.JadxFilesGetter
import jadx.core.plugins.AppContext
import jadx.core.plugins.PluginRuntime
import jadx.gui.ui.MainWindow

/**
 * 收集全部插件。
 *
 * **做什么**：
 * - 若包装器里的 [JadxDecompiler] 已初始化，直接返回其已解析且已初始化的插件；
 * - 否则在临时上下文中加载并初始化插件（不带 GUI 上下文），
 *   再把「全局插件」与「项目插件」合并返回；临时反编译器不关闭以保留共享临时文件。
 *
 * **注意**：`JadxArgs.filesGetter` 是 Kotlin 属性，这里用属性赋值而非
 * `setFilesGetter()`（Kotlin 不允许显式调用 Kotlin 属性的 getter/setter）。
 */
class CollectPlugins(private val mainWindow: MainWindow) {

	fun build(): List<PluginRuntime> {
		val currentDecompiler: JadxDecompiler? = mainWindow.getWrapper().currentDecompiler
		if (currentDecompiler != null) {
			return currentDecompiler.getPluginManager().resolvedPlugins
				.filter { plugin -> plugin.isInitialized }
		}
		val globalPlugins = mainWindow.getGuiPluginsManager().globalPlugins
		// 在临时上下文中收集并初始化插件
		val jadxArgs = mainWindow.getSettings().toJadxArgs()
		jadxArgs.filesGetter = JadxFilesGetter.INSTANCE
		// decompiler 仅用于插件初始化，不关闭以保留共享的临时文件
		val decompiler = JadxDecompiler(jadxArgs)
		val pluginManager = decompiler.getPluginManager()
		pluginManager.registerAddPluginListener { pluginRuntime ->
			val appContext = AppContext()
			appContext.setGuiContext(null) // 加载临时插件时不带 UI 上下文
			appContext.setFilesGetter(jadxArgs.filesGetter)
			pluginRuntime.appContext = appContext
		}
		pluginManager.load(mainWindow.getGuiPluginsManager().buildProjectPluginLoader())
		val allPlugins = pluginManager.allPlugins
		pluginManager.init(decompiler, allPlugins)
		// 全局插件在未打开工程时没有 plugin context
		val plugins = ArrayList<PluginRuntime>(globalPlugins.size + allPlugins.size)
		plugins.addAll(globalPlugins)
		allPlugins.filterTo(plugins) { plugin -> plugin.isInitialized }
		pluginManager.unload(allPlugins)
		return plugins
	}
}
