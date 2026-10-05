package kadx.gui.settings.ui.plugins

/**
 * 插件列表中一个条目的抽象基类。
 *
 * **做什么**：为「已安装 / 可用 / 已加载 / 标题」等不同节点提供统一的信息访问接口，
 * 由 [PluginSettingsGroup] 渲染到列表与详情面板中。
 *
 * **为什么返回可空**：原 Java 基类各 getter 直接 `return null`，
 * 只有具体子类才返回真实值，因此这里如实标注可空。
 */
abstract class BasePluginListNode {

	abstract fun getTitle(): String?

	abstract fun hasDetails(): Boolean

	open fun getPluginId(): String? = null

	open fun getDescription(): String? = null

	open fun getHomepage(): String? = null

	open fun getLocationId(): String? = null

	open fun getVersion(): String? = null

	open fun isDisabled(): Boolean = false

	open fun getAction(): PluginAction = PluginAction.NONE
}
