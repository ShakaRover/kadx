package kadx.gui.settings.ui

import kadx.api.plugins.gui.ISettingsGroup

/**
 * 带有子设置组的 [SettingsGroup]。
 *
 * **做什么**：在自身页面之外，还维护一个子节点列表，供设置树构建层级结构。
 */
class SubSettingsGroup(title: String) : SettingsGroup(title) {

	private val groups: MutableList<ISettingsGroup> = ArrayList()

	/**
	 * 返回可变的子组列表（协变返回 [MutableList]），
	 * 这样 Java 调用方仍可直接 `getSubGroups().add(...)`。
	 */
	override fun getSubGroups(): MutableList<ISettingsGroup> = groups
}
