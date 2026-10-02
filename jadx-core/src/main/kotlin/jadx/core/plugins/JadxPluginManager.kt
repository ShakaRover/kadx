package jadx.core.plugins

import jadx.api.JadxDecompiler
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.OptionDescription
import jadx.core.plugins.versions.VerifyRequiredVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Objects
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.function.Consumer

/**
 * 插件管理器：加载、注册、解析、初始化与卸载插件。
 *
 * **做什么**：
 * - `load` / `register`：把插件包装为 [PluginContext] 并加入 `allPlugins`；
 * - `resolve`：处理多个插件提供同一功能（`provides`）的冲突，选出 `resolvedPlugins`；
 * - `init` / `unload`：对插件执行生命周期回调；
 * - 对外暴露已加载 / 已解析的插件集合与全部代码输入。
 *
 * **为什么用 `SortedSet` + [TreeSet]**：插件需要按 id 稳定排序，
 * 便于测试与 GUI 展示；[PluginContext] 已实现 `Comparable`。
 *
 * **K2 注意**：原 Java 的 `private synchronized void resolve()` 转成普通函数 +
 * [Synchronized] 注解。
 */
class JadxPluginManager(private val decompiler: JadxDecompiler) {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxPluginManager::class.java)
	}

	private val pluginsData: JadxPluginsData = JadxPluginsData(decompiler, this)
	private val disabledPlugins: Set<String> = decompiler.getArgs().disabledPlugins
	private val allPlugins: SortedSet<PluginContext> = TreeSet()
	private val resolvedPlugins: SortedSet<PluginContext> = TreeSet()
	private val provideSuggestions: MutableMap<String, String> = TreeMap()

	private val addPluginListeners: MutableList<Consumer<PluginContext>> = ArrayList()

	/**
	 * 添加冲突解决建议：当多个插件提供同一 `provides` 时，优先选择 [pluginId]。
	 */
	fun providesSuggestion(provides: String, pluginId: String) {
		provideSuggestions[provides] = pluginId
	}

	/** 通过加载器加载全部插件，并解析出可用集合。 */
	fun load(pluginLoader: JadxPluginLoader) {
		allPlugins.clear()
		val verifyRequiredVersion = VerifyRequiredVersion()
		for (plugin in pluginLoader.load()) {
			addPlugin(plugin, verifyRequiredVersion)
		}
		resolve()
	}

	/** 注册单个插件（已存在或版本不兼容时跳过），并重新解析。 */
	fun register(plugin: JadxPlugin) {
		Objects.requireNonNull(plugin)
		val addedPlugin = addPlugin(plugin, VerifyRequiredVersion())
		if (addedPlugin == null) {
			LOG.debug("Can't register plugin, it was disabled: {}", plugin.getPluginInfo().getPluginId())
			return
		}
		LOG.debug("Register plugin: {}", addedPlugin.getPluginId())
		resolve()
	}

	/**
	 * 把插件包装为 [PluginContext] 并加入 `allPlugins`。
	 *
	 * @return 插件被禁用或版本不兼容时返回 null
	 */
	private fun addPlugin(plugin: JadxPlugin, verifyRequiredVersion: VerifyRequiredVersion): PluginContext? {
		val pluginContext = PluginContext(decompiler, pluginsData, plugin)
		if (disabledPlugins.contains(pluginContext.getPluginId())) {
			return null
		}
		val requiredJadxVersion = pluginContext.getPluginInfo().getRequiredJadxVersion()
		if (!verifyRequiredVersion.isCompatible(requiredJadxVersion)) {
			LOG.warn(
				"Plugin '{}' not loaded: requires '{}' jadx version which it is not compatible with current: {}",
				pluginContext,
				requiredJadxVersion,
				verifyRequiredVersion.getJadxVersion(),
			)
			return null
		}
		LOG.debug("Loading plugin: {}", pluginContext)
		if (!allPlugins.add(pluginContext)) {
			throw IllegalArgumentException("Duplicate plugin id: " + pluginContext + ", class " + plugin.javaClass)
		}
		addPluginListeners.forEach { l -> l.accept(pluginContext) }
		return pluginContext
	}

	/** 按插件 id 卸载，并重新解析。 */
	fun unload(pluginId: String): Boolean {
		val result = allPlugins.removeIf { context ->
			if (context.getPluginId() == pluginId) {
				LOG.debug("Unload plugin: {}", pluginId)
				true
			} else {
				false
			}
		}
		resolve()
		return result
	}

	fun getAllPluginContexts(): SortedSet<PluginContext> = allPlugins

	fun getResolvedPluginContexts(): SortedSet<PluginContext> = resolvedPlugins

	/**
	 * 解析插件冲突：对每个 `provides` 分组，若只有一个插件则直接采用；
	 * 多个时优先用 [provideSuggestions] 的建议，否则取分组内第一个并记日志。
	 */
	@Synchronized
	private fun resolve() {
		val provides: Map<String, List<PluginContext>> = allPlugins.groupBy { p -> p.getPluginInfo().getProvides() }
		val resolved = ArrayList<PluginContext>(provides.size)
		provides.forEach { (provide, list) ->
			if (list.size == 1) {
				resolved.add(list[0])
			} else {
				val suggestion = provideSuggestions[provide]
				if (suggestion != null) {
					list.stream()
						.filter { p -> p.getPluginId() == suggestion }
						.findFirst()
						.ifPresent { resolved.add(it) }
				} else {
					val selected = list[0]
					resolved.add(selected)
					LOG.debug("Select providing '{}' plugin '{}', candidates: {}", provide, selected, list)
				}
			}
		}
		resolvedPlugins.clear()
		resolvedPlugins.addAll(resolved)
	}

	/** 初始化全部插件。 */
	fun initAll() {
		init(allPlugins)
	}

	/** 仅初始化已解析的插件。 */
	fun initResolved() {
		init(resolvedPlugins)
	}

	/** 依次初始化给定插件集合：注入默认上下文、调用 init，最后校验选项描述。 */
	fun init(pluginContexts: SortedSet<PluginContext>) {
		val defAppContext = buildDefaultAppContext()
		for (context in pluginContexts) {
			try {
				if (context.getAppContext() == null) {
					context.setAppContext(defAppContext)
				}
				context.init()
			} catch (e: Exception) {
				LOG.error("Failed to init plugin: {}", context.getPluginId(), e)
			}
		}
		for (context in pluginContexts) {
			val options = context.getOptions()
			if (options != null) {
				verifyOptions(context, options)
			}
		}
	}

	/** 卸载全部插件。 */
	fun unloadAll() {
		unload(allPlugins)
	}

	/** 仅卸载已解析的插件。 */
	fun unloadResolved() {
		unload(resolvedPlugins)
	}

	/** 依次卸载给定插件集合，单个失败只记警告。 */
	fun unload(pluginContexts: SortedSet<PluginContext>) {
		for (context in pluginContexts) {
			try {
				context.unload()
			} catch (e: Exception) {
				LOG.warn("Failed to unload plugin: {}", context.getPluginId(), e)
			}
		}
	}

	/** 构造默认应用上下文：无 GUI 上下文，文件目录来自 jadx 参数。 */
	private fun buildDefaultAppContext(): AppContext {
		val appContext = AppContext()
		appContext.setGuiContext(null)
		appContext.setFilesGetter(decompiler.getArgs().filesGetter)
		return appContext
	}

	/** 校验插件选项描述：名称必须以插件 id 为前缀，且说明/取值列表不为空。 */
	@Suppress("SENSELESS_COMPARISON")
	private fun verifyOptions(pluginContext: PluginContext, options: JadxPluginOptions) {
		val pluginId = pluginContext.getPluginId()
		val descriptions = options.getOptionsDescriptions()
		if (descriptions == null) {
			throw IllegalArgumentException("Null option descriptions in plugin id: " + pluginId)
		}
		val prefix = "$pluginId."
		descriptions.forEach { descObj ->
			val optName = descObj.name()
			if (optName == null || !optName.startsWith(prefix)) {
				throw IllegalArgumentException("Plugin option name should start with plugin id: '$prefix', option: " + optName)
			}
			val desc = descObj.description()
			if (desc == null || desc.isEmpty()) {
				throw IllegalArgumentException("Plugin option description not set, plugin: " + pluginId)
			}
			val values = descObj.values()
			if (values == null) {
				throw IllegalArgumentException("Plugin option values is null, option: " + optName + ", plugin: " + pluginId)
			}
		}
	}

	/** 汇总所有已解析插件注册的代码输入。 */
	fun getCodeInputs(): List<JadxCodeInput> = getResolvedPluginContexts().flatMap { p -> p.getCodeInputs() }

	/** 注册插件添加监听器，并立即对已存在的插件回调一次。 */
	fun registerAddPluginListener(listener: Consumer<PluginContext>) {
		this.addPluginListeners.add(listener)
		// 对已添加的插件立即执行一次
		getAllPluginContexts().forEach { p -> listener.accept(p) }
	}
}
