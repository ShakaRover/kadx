package jadx.gui.plugins

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.gui.plugins.JadxGlobalGuiPlugin
import jadx.api.gui.plugins.JadxGuiContextExt
import jadx.api.plugins.events.types.ReloadSettingsWindow
import jadx.api.plugins.gui.JadxGuiContext
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.cli.JadxAppCommon
import jadx.cli.plugins.JadxFilesGetter
import jadx.core.plugins.AppContext
import jadx.core.plugins.JadxPluginManager
import jadx.core.plugins.PluginRuntime
import jadx.gui.plugins.context.CommonGuiPluginsContext
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.plugins.tools.JadxExternalPluginsLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.SortedSet

/**
 * jadx-gui 插件管理器：管理「全局作用域」插件，并把它们注入到每个工程的反编译器中。
 *
 * **做什么**：
 * - [load]：加载全局插件（仅 [JadxGlobalGuiPlugin] 子类）并执行 global init；
 * - [initGuiPluginsContext]：为某个插件管理器注册监听器，为每个插件构建 GUI 上下文；
 * - [injectGlobalPlugins]：把全局插件实例注册到工程反编译器，并复制选项与自定义设置；
 * - [resetProjectScope]：工程关闭时清理项目级扩展点，保留全局扩展点。
 */
open class GuiPluginsManager(private val mainWindow: MainWindow) {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(GuiPluginsManager::class.java)
	}

	private val globalArgs: JadxArgs
	private val globalPluginManager: JadxPluginManager
	private val guiPluginsContext: CommonGuiPluginsContext

	init {
		this.guiPluginsContext = CommonGuiPluginsContext(mainWindow)
		this.globalArgs = buildJadxArgs(mainWindow.getSettings())
		this.globalPluginManager = JadxPluginManager(globalArgs)
	}

	fun load() {
		try {
			val start = System.currentTimeMillis()

			initGuiPluginsContextForGlobalScope()
			globalPluginManager.load(
				JadxExternalPluginsLoader { cls -> JadxGlobalGuiPlugin::class.java.isAssignableFrom(cls) },
			)
			val globalPlugins = globalPluginManager.resolvedPlugins
			runGlobalInit(globalPlugins)
			if (globalPlugins.isNotEmpty()) {
				// settings window can be opened before global plugins init
				mainWindow.events().send(ReloadSettingsWindow.INSTANCE)
			}

			if (LOG.isDebugEnabled) {
				LOG.debug(
					"Initialized {} global gui plugins in {} ms",
					globalPlugins.size,
					System.currentTimeMillis() - start,
				)
			}
		} catch (e: Exception) {
			LOG.error("Failed to load gui plugins", e)
		}
	}

	open fun buildProjectPluginLoader(): JadxPluginLoader = JadxExternalPluginsLoader { cls -> !JadxGlobalGuiPlugin::class.java.isAssignableFrom(cls) }

	fun initGuiPluginsContextForGlobalScope() {
		initGuiPluginsContext(globalPluginManager, globalArgs, true)
	}

	fun initGuiPluginsContext(pluginManager: JadxPluginManager, jadxArgs: JadxArgs, isGlobalPlugin: Boolean) {
		pluginManager.registerAddPluginListener { pluginRuntime ->
			val appContext = AppContext()
			appContext.setGuiContext(guiPluginsContext.buildForPlugin(pluginRuntime, isGlobalPlugin))
			appContext.setFilesGetter(jadxArgs.filesGetter)
			pluginRuntime.appContext = appContext
		}
	}

	/**
	 * Inject global plugin into project decompiler and transfer plugin context data.
	 */
	fun injectGlobalPlugins(decompiler: JadxDecompiler) {
		for (globalPlugin in globalPlugins) {
			val projectPlugin = decompiler.getPluginManager().register(globalPlugin.pluginInstance)
			if (projectPlugin != null) {
				// copy options and gui data
				projectPlugin.registerOptions(globalPlugin.options)
				guiPluginsContext.copyGlobalPluginData(globalPlugin, projectPlugin)
			} else {
				LOG.warn("Failed to register plugin in project decompiler: {}", globalPlugin.pluginId)
			}
		}
	}

	val globalPlugins: SortedSet<PluginRuntime> get() = globalPluginManager.resolvedPlugins

	fun resetProjectScope() {
		guiPluginsContext.resetProjectScope()
	}

	fun runGlobalInit(globalPlugins: SortedSet<PluginRuntime>) {
		val failedPlugins = ArrayList<String>()
		for (pluginRuntime in globalPlugins) {
			try {
				val plugin = pluginRuntime.pluginInstance as JadxGlobalGuiPlugin
				PluginRuntime.classLoaderWrap(plugin.javaClass.classLoader) {
					val appContext = pluginRuntime.appContext
					if (appContext != null) {
						val guiContext: JadxGuiContext = checkNotNull(appContext.getGuiContext())
						plugin.globalInit(guiContext as JadxGuiContextExt)
					}
				}
			} catch (e: Throwable) {
				LOG.warn("Failed to init global gui plugin: {}", pluginRuntime.pluginId, e)
				failedPlugins.add(pluginRuntime.pluginId)
			}
		}
		// don't inject failed plugins into projects
		failedPlugins.forEach { globalPluginManager.unload(it) }
	}

	@Synchronized
	fun runGlobalUnload() {
		try {
			for (pluginRuntime in globalPlugins) {
				try {
					val plugin = pluginRuntime.pluginInstance as JadxGlobalGuiPlugin
					PluginRuntime.classLoaderWrap(plugin.javaClass.classLoader) { plugin.globalUnload() }
				} catch (e: Exception) {
					LOG.warn("Failed to unload global gui plugin: {}", pluginRuntime.pluginId, e)
				}
			}
		} catch (e: Exception) {
			LOG.warn("Failed to unload global gui plugins", e)
		}
	}

	fun getPluginsContext(): CommonGuiPluginsContext = guiPluginsContext

	fun getGlobalPluginManager(): JadxPluginManager = globalPluginManager

	private fun buildJadxArgs(settings: JadxSettings): JadxArgs {
		val jadxArgs = settings.toJadxArgs()
		jadxArgs.filesGetter = JadxFilesGetter.INSTANCE
		JadxAppCommon.applyEnvVars(jadxArgs)
		return jadxArgs
	}
}
