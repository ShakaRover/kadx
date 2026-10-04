package jadx.core.plugins

import jadx.api.JadxArgs
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.options.JadxPluginOptions
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 插件运行时：把插件实例、元信息、类加载器、应用上下文、项目上下文与选项聚合在一起。
 *
 * **做什么**：承载插件生命周期（`init` / `unload`，均在插件类加载器上下文中执行）、
 * 选项注册与应用，以及按插件 id 的比较语义。
 *
 * **为什么与 [PluginContext] 分离**：GUI 的「全局作用域」插件在打开工程前就需要存在，
 * 此时还没有 [PluginContext]（它绑定到某个 `JadxDecompiler`）。因此把全局选项、
 * 应用上下文等「无工程」状态放在 [PluginRuntime] 中。
 *
 * **Kotlin 转换说明**：公共访问通过属性暴露，JVM 侧仍生成 `getPluginId()` /
 * `getPluginInfo()` / `getPluginInstance()` / `getAppContext()` / `getPluginContext()` /
 * `getOptions()` 等，Java 调用方与插件 API 零改动。
 */
class PluginRuntime(
	private val plugin: JadxPlugin,
	private val jadxArgs: JadxArgs,
) : Comparable<PluginRuntime> {

	val pluginInfo: JadxPluginInfo = plugin.getPluginInfo()
	private val pluginClassLoader: ClassLoader = plugin.javaClass.classLoader

	/** 应用级上下文；由宿主在 init 前注入。 */
	var appContext: AppContext? = null

	/** 项目级上下文；仅在已初始化（`init` 成功）后非空。 */
	var pluginContext: PluginContext? = null
		private set

	/**
	 * 插件选项暂存在此处，以支持「无工程（decompiler）」的全局选项。
	 * TODO: improve API to distinguish global and project options for jadx-gui
	 */
	var options: JadxPluginOptions? = null
		private set

	val pluginInstance: JadxPlugin get() = plugin

	val pluginId: String get() = pluginInfo.getPluginId()

	val isInitialized: Boolean get() = pluginContext != null

	/** 初始化插件：在插件类加载器上下文中调用 [JadxPlugin.init]。 */
	fun init(pluginContext: PluginContext) {
		this.pluginContext = pluginContext
		classLoaderWrap {
			try {
				plugin.init(pluginContext)
			} catch (e: Throwable) {
				LOG.error("Plugin init failed", e)
				this.pluginContext = null
			}
		}
	}

	/** 卸载插件：仅在已初始化时在插件类加载器上下文中调用 [JadxPlugin.unload]。 */
	fun unload() {
		if (pluginContext != null) {
			try {
				classLoaderWrap { plugin.unload() }
			} finally {
				pluginContext = null
			}
		}
	}

	/** 临时把当前线程的上下文类加载器切换为插件类加载器，执行任务后恢复。 */
	fun classLoaderWrap(task: Runnable) {
		classLoaderWrap(pluginClassLoader, task)
	}

	/** 应用插件选项，并把 jadx 参数中保存的选项值设置给插件。 */
	fun registerOptions(options: JadxPluginOptions?) {
		if (options == null) {
			return
		}
		this.options = options
		try {
			options.setOptions(jadxArgs.pluginOptions)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to apply options for plugin: " + pluginId, e)
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is PluginRuntime) {
			return false
		}
		return this.pluginId == other.pluginId
	}

	override fun hashCode(): Int = pluginId.hashCode()

	override fun compareTo(other: PluginRuntime): Int = this.pluginId.compareTo(other.pluginId)

	override fun toString(): String = pluginId

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(PluginRuntime::class.java)

		@JvmStatic
		fun classLoaderWrap(pluginClassLoader: ClassLoader, task: Runnable) {
			val thread = Thread.currentThread()
			val prevClassLoader = thread.contextClassLoader
			thread.contextClassLoader = pluginClassLoader
			try {
				task.run()
			} finally {
				thread.contextClassLoader = prevClassLoader
			}
		}
	}
}
