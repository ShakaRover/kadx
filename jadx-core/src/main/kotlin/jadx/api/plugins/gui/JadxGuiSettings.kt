package jadx.api.plugins.gui

import jadx.api.plugins.options.OptionDescription

/**
 * GUI 设置接口：插件通过它自定义设置页。
 *
 * **做什么**：[setCustomSettingsGroup] 设置插件自己的设置页；
 * [buildSettingsGroupForOptions] 是一个便捷方法，只根据给定选项列表构建设置组。
 *
 * **为什么 [buildSettingsGroupForOptions] 用显式 `java.util.List`**：
 * Kotlin 的 `List<T>` 在参数位置会生成 `List<? extends T>`，而 Java 实现类
 * `GuiSettingsContext` 覆写时声明的是 `List<OptionDescription>`，会产生 name clash；
 * 显式 `java.util.List` 才能让 Java 实现类零改动。
 */
interface JadxGuiSettings {

	/**
	 * 设置插件自定义设置页。
	 */
	fun setCustomSettingsGroup(group: ISettingsGroup)

	/**
	 * 便捷方法：仅为给定的选项列表构建设置组。
	 */
	fun buildSettingsGroupForOptions(title: String, options: java.util.List<OptionDescription>): ISettingsGroup
}
