package jadx.gui.plugins.context

import jadx.core.plugins.PluginContext
import jadx.gui.settings.data.ITabStatePersist
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.codearea.JNodePopupBuilder
import jadx.gui.utils.ui.ActionHandler
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * GUI 插件的公共上下文：集中保存所有插件注册到界面的扩展点。
 *
 * **做什么**：为每个插件构建 [GuiPluginContext]，并收集代码区弹窗动作、
 * 树节点弹窗项、输入分类、标签页状态适配器等；同时负责把插件菜单项加入主窗口。
 *
 * **线程模型**：保持原 Swing 模型，菜单动作交由主窗口的后台执行器运行。
 */
class CommonGuiPluginsContext(private val mainWindow: MainWindow) {

	private val pluginsMap: MutableMap<PluginContext, GuiPluginContext> = HashMap()

	private val codePopupActions: MutableList<CodePopupAction> = ArrayList()
	private val treePopupMenus: MutableList<TreePopupMenuEntry> = ArrayList()
	private val treeInputs: MutableList<ITreeInputCategory> = ArrayList()
	private val tabStateAdapters: MutableList<ITabStatePersist> = ArrayList()

	/** 为某个插件构建并登记其 GUI 上下文。 */
	fun buildForPlugin(pluginContext: PluginContext): GuiPluginContext {
		val guiPluginContext = GuiPluginContext(this, pluginContext)
		pluginsMap[pluginContext] = guiPluginContext
		return guiPluginContext
	}

	/** 按插件上下文取回其 GUI 上下文；不存在时返回 `null`。 */
	fun getPluginGuiContext(pluginContext: PluginContext): GuiPluginContext? = pluginsMap[pluginContext]

	/** 按插件 ID 取回其 GUI 上下文；不存在时返回 `null`。 */
	fun getGuiPluginContextById(pluginId: String): GuiPluginContext? {
		for (guiPluginContext in pluginsMap.values) {
			if (guiPluginContext.getPluginContext().getPluginId() == pluginId) {
				return guiPluginContext
			}
		}
		return null
	}

	/** 清空界面扩展点并重置插件菜单。 */
	fun reset() {
		codePopupActions.clear()
		treePopupMenus.clear()
		treeInputs.clear()
		mainWindow.resetPluginsMenu()
	}

	fun getMainWindow(): MainWindow = mainWindow

	fun getCodePopupActionList(): MutableList<CodePopupAction> = codePopupActions

	fun getTreePopupMenuEntries(): MutableList<TreePopupMenuEntry> = treePopupMenus

	fun getTreeInputCategories(): MutableList<ITreeInputCategory> = treeInputs

	fun getTabStatePersistAdapters(): MutableList<ITabStatePersist> = tabStateAdapters

	/** 向「插件」菜单添加一项，点击后在后台执行 [action]。 */
	fun addMenuAction(name: String, action: Runnable) {
		val item = ActionHandler(
			Consumer {
				try {
					mainWindow.getBackgroundExecutor().execute(name, action)
				} catch (e: Exception) {
					LOG.error("Error running action for menu item: {}", name, e)
				}
			},
		)
		item.setNameAndDesc(name)
		mainWindow.addToPluginsMenu(item)
	}

	/** 把所有代码区弹窗动作追加到给定弹窗构建器（若有）。 */
	fun appendPopupMenus(codeArea: CodeArea, popup: JNodePopupBuilder) {
		if (codePopupActions.isEmpty()) {
			return
		}
		popup.addSeparator()
		for (codePopupAction in codePopupActions) {
			popup.add(codePopupAction.buildAction(codeArea))
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(CommonGuiPluginsContext::class.java)
	}
}
