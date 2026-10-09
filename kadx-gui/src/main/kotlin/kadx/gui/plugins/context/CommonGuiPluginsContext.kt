package kadx.gui.plugins.context

import kadx.core.plugins.PluginRuntime
import kadx.core.utils.Utils
import kadx.gui.settings.data.ITabStatePersist
import kadx.gui.ui.MainWindow
import kadx.gui.ui.codearea.CodeArea
import kadx.gui.ui.codearea.JNodePopupBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * GUI 插件的公共上下文：集中保存所有插件注册到界面的扩展点。
 *
 * **做什么**：为每个插件构建 [GuiPluginContext]，并按「全局 / 项目」两个作用域
 * 收集代码区弹窗动作、树节点弹窗项、输入分类、标签页状态适配器等。
 *
 * **作用域语义**：全局插件的扩展点在工程关闭后保留；项目插件的扩展点在
 * [resetProjectScope] 时清理。
 *
 * **线程模型**：保持原 Swing 模型。
 */
class CommonGuiPluginsContext(private val mainWindow: MainWindow) {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CommonGuiPluginsContext::class.java)
	}

	private val globalScope = GuiPluginsRegistry()
	private val projectScope = GuiPluginsRegistry()

	private val globalPlugins: MutableMap<PluginRuntime, GuiPluginContext> = HashMap()
	private val projectPlugins: MutableMap<PluginRuntime, GuiPluginContext> = HashMap()

	/** 为某个插件构建并登记其 GUI 上下文（区分全局 / 项目作用域）。 */
	fun buildForPlugin(pluginRuntime: PluginRuntime, isGlobalPlugin: Boolean): GuiPluginContext {
		val registry = if (isGlobalPlugin) globalScope else projectScope
		val guiPluginContext = GuiPluginContext(this, registry, pluginRuntime)
		(if (isGlobalPlugin) globalPlugins else projectPlugins)[pluginRuntime] = guiPluginContext
		return guiPluginContext
	}

	/** 把全局插件的自定义设置页复制到对应的项目插件上下文。 */
	fun copyGlobalPluginData(globalContext: PluginRuntime, projectContext: PluginRuntime) {
		val globalGuiContext = globalPlugins[globalContext]
		val projectGuiContext = projectPlugins[projectContext]
		if (globalGuiContext != null && projectGuiContext != null) {
			projectGuiContext.setCustomSettings(globalGuiContext.customSettingsGroup)
		}
	}

	/** 清理项目级扩展点。 */
	fun resetProjectScope() {
		projectScope.clear()
		projectPlugins.clear()
	}

	fun getMainWindow(): MainWindow = mainWindow

	fun getCodePopupActionList(): List<CodePopupAction> = Utils.mergeLists(globalScope.codePopupActions, projectScope.codePopupActions) ?: emptyList()

	fun getTreePopupMenuEntries(): List<TreePopupMenuEntry> = Utils.mergeLists(globalScope.treePopupMenuEntries, projectScope.treePopupMenuEntries) ?: emptyList()

	fun getTreeInputCategories(): List<ITreeInputCategory> = Utils.mergeLists(globalScope.treeInputCategories, projectScope.treeInputCategories) ?: emptyList()

	fun getTabStatePersistAdapters(): List<ITabStatePersist> = Utils.mergeLists(globalScope.tabStatePersistAdapters, projectScope.tabStatePersistAdapters) ?: emptyList()

	/** 把所有代码区弹窗动作追加到给定弹窗构建器（若有）。 */
	fun appendPopupMenus(codeArea: CodeArea, popup: JNodePopupBuilder) {
		val codePopupActionList = getCodePopupActionList()
		if (codePopupActionList.isEmpty()) {
			return
		}
		popup.addSeparator()
		for (codePopupAction in codePopupActionList) {
			popup.add(codePopupAction.buildAction(codeArea))
		}
	}
}
