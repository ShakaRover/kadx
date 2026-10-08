package kadx.gui.plugins.context

import kadx.api.JavaClass
import kadx.api.JavaNode
import kadx.api.KadxDecompiler
import kadx.api.gui.IMainWindow
import kadx.api.gui.plugins.KadxGuiContextExt
import kadx.api.gui.tree.ITreeNode
import kadx.api.metadata.ICodeNodeRef
import kadx.api.plugins.events.types.NodeRenamedByUser
import kadx.api.plugins.gui.ISettingsGroup
import kadx.api.plugins.gui.KadxGuiSettings
import kadx.api.plugins.options.KadxPluginOptions
import kadx.core.plugins.PluginContext
import kadx.core.plugins.PluginRuntime
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.settings.data.ITabStatePersist
import kadx.gui.treemodel.JNode
import kadx.gui.ui.codearea.AbstractCodeArea
import kadx.gui.ui.codearea.AbstractCodeContentPanel
import kadx.gui.ui.codearea.CodeArea
import kadx.gui.ui.dialog.UsageDialog
import kadx.gui.utils.IconsCache
import kadx.gui.utils.UiUtils
import org.slf4j.LoggerFactory
import java.awt.Container
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * kadx-gui 的插件上下文实现。
 *
 * **做什么**：把核心 [KadxGuiContext] 接口接到实际的 Swing 界面上：
 * UI 线程调度、菜单/弹窗/快捷键注册、剪贴板、图标、节点查询与重命名等。
 *
 * **线程模型**：保持 Swing 模型（`UiUtils.uiRun` / `invokeLater`），UI 更新在 EDT 上执行。
 */
class GuiPluginContext internal constructor(
	private val commonContext: CommonGuiPluginsContext,
	private val registry: GuiPluginsRegistry,
	private val pluginRuntime: PluginRuntime,
) : KadxGuiContextExt {

	private var customSettings: ISettingsGroup? = null

	fun getCommonContext(): CommonGuiPluginsContext = commonContext

	override fun getMainWindow(): IMainWindow = commonContext.getMainWindow()

	override fun registerOptions(options: KadxPluginOptions) {
		pluginRuntime.registerOptions(options)
	}

	/** 插件 id。 */
	val pluginId: String get() = pluginRuntime.pluginId

	/**
	 * Internal method with plugin load guard.
	 */
	internal fun getPluginContext(): PluginContext = pluginRuntime.pluginContext
		?: throw KadxRuntimeException("Plugin not yet loaded")

	override fun getMainFrame(): JFrame = commonContext.getMainWindow()

	override fun uiRun(runnable: Runnable) {
		UiUtils.uiRun(runnable)
	}

	override fun addMenuAction(name: String, action: Runnable) {
		commonContext.addMenuAction(registry, name, action)
	}

	override fun addPopupMenuAction(
		name: String,
		enabled: Function<ICodeNodeRef, Boolean>?,
		keyBinding: String?,
		action: Consumer<ICodeNodeRef>,
	) {
		// 插件 API 使用 Java 函数类型，内部实现改用 Kotlin 函数类型，这里做一次适配
		val enabledCheck: ((ICodeNodeRef) -> Boolean)? = enabled?.let { f -> { node -> f.apply(node) } }
		registry.codePopupActions.add(CodePopupAction(name, enabledCheck, keyBinding) { node -> action.accept(node) })
	}

	override fun addTreePopupMenuEntry(name: String, addPredicate: Predicate<ITreeNode>, action: Consumer<ITreeNode>) {
		registry.treePopupMenuEntries.add(TreePopupMenuEntry(name, { node -> addPredicate.test(node) }) { node -> action.accept(node) })
	}

	/** 注册一个输入分类器。 */
	fun registerTreeInputCategory(inputCategory: ITreeInputCategory) {
		registry.treeInputCategories.add(inputCategory)
	}

	/** 注册一个标签页状态持久化适配器。 */
	fun registerTabStatePersistAdapter(tabStatePersist: ITabStatePersist) {
		registry.tabStatePersistAdapters.add(tabStatePersist)
	}

	override fun registerGlobalKeyBinding(id: String, keyBinding: String, action: Runnable): Boolean {
		val keyStroke = KeyStroke.getKeyStroke(keyBinding)
			?: throw IllegalArgumentException("Failed to parse key binding: " + keyBinding)
		val mainPanel = commonContext.getMainWindow().getContentPane() as JPanel
		val prevBinding = mainPanel.getInputMap().get(keyStroke)
		if (prevBinding != null) {
			return false
		}
		UiUtils.addKeyBinding(mainPanel, keyStroke, id, action)
		return true
	}

	override fun copyToClipboard(str: String) {
		UiUtils.copyToClipboard(str)
	}

	override fun settings(): KadxGuiSettings = GuiSettingsContext(this)

	internal fun setCustomSettings(customSettingsGroup: ISettingsGroup?) {
		this.customSettings = customSettingsGroup
	}

	/** 插件自定义设置页；未设置时为 `null`。 */
	val customSettingsGroup: ISettingsGroup? get() = customSettings

	/** 当前选中的代码区；当前标签不是代码区时返回 `null`。 */
	private val codeArea: CodeArea? get() {
		val contentPane: Container? = commonContext.getMainWindow().getTabbedPane().selectedContentPanel
		if (contentPane is AbstractCodeContentPanel) {
			val codeArea: AbstractCodeArea? = contentPane.getCodeArea()
			if (codeArea is CodeArea) {
				return codeArea
			}
		}
		return null
	}

	override fun getSVGIcon(name: String): ImageIcon = try {
		IconsCache.getSVGIcon(name)
	} catch (e: Exception) {
		LOG.error("Failed to load icon: {}", name, e)
		IconsCache.getSVGIcon("ui/error")
	}

	override fun getNodeUnderCaret(): ICodeNodeRef? {
		val codeArea = codeArea
		if (codeArea != null) {
			val nodeUnderCaret = codeArea.nodeUnderCaret
			if (nodeUnderCaret != null) {
				return nodeUnderCaret.getCodeNodeRef()
			}
		}
		return null
	}

	override fun getNodeUnderMouse(): ICodeNodeRef? {
		val codeArea = codeArea
		if (codeArea != null) {
			val nodeUnderMouse = codeArea.nodeUnderMouse
			if (nodeUnderMouse != null) {
				return nodeUnderMouse.getCodeNodeRef()
			}
		}
		return null
	}

	override fun getEnclosingNodeUnderCaret(): ICodeNodeRef? {
		val codeArea = codeArea
		if (codeArea != null) {
			val nodeUnderCaret = codeArea.enclosingNodeUnderCaret
			if (nodeUnderCaret != null) {
				return nodeUnderCaret.getCodeNodeRef()
			}
		}
		return null
	}

	override fun getEnclosingNodeUnderMouse(): ICodeNodeRef? {
		val codeArea = codeArea
		if (codeArea != null) {
			val nodeUnderMouse = codeArea.enclosingNodeUnderMouse
			if (nodeUnderMouse != null) {
				return nodeUnderMouse.getCodeNodeRef()
			}
		}
		return null
	}

	override fun open(ref: ICodeNodeRef): Boolean {
		commonContext.getMainWindow().getTabsController().codeJump(getJNodeFromRef(ref))
		return true
	}

	override fun openUsageDialog(ref: ICodeNodeRef) {
		UsageDialog.open(commonContext.getMainWindow(), getJNodeFromRef(ref))
	}

	private fun getJNodeFromRef(ref: ICodeNodeRef): JNode = checkNotNull(commonContext.getMainWindow().getCacheObject().nodeCache.makeFrom(ref))

	override fun reloadActiveTab() {
		UiUtils.uiRun(
			Runnable {
				val codeArea = codeArea
				codeArea?.refreshClass()
			},
		)
	}

	override fun reloadAllTabs() {
		UiUtils.uiRun(
			Runnable {
				for (contentPane in commonContext.getMainWindow().getTabbedPane().tabs) {
					if (contentPane is AbstractCodeContentPanel) {
						val codeArea = contentPane.getCodeArea()
						if (codeArea is CodeArea) {
							codeArea.refreshClass()
						}
					}
				}
			},
		)
	}

	override fun applyNodeRename(nodeRef: ICodeNodeRef) {
		val decompiler: KadxDecompiler = commonContext.getMainWindow().getWrapper().getDecompiler()
		val javaNode: JavaNode = decompiler.getJavaNodeByRef(nodeRef)
			?: throw KadxRuntimeException("Failed to resolve node ref: $nodeRef")
		val newName: String = if (javaNode is JavaClass) {
			// 包名可能有别名
			javaNode.getFullName()
		} else {
			checkNotNull(javaNode.getName())
		}
		val events = commonContext.getMainWindow().events()
		events.send(NodeRenamedByUser(nodeRef, "", newName))
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(GuiPluginContext::class.java)
	}
}
