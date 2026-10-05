package kadx.api.plugins.gui

import java.util.Collections
import javax.swing.JComponent

/**
 * 设置页自定义接口。
 *
 * **做什么**：插件实现它来向 kadx-gui 设置窗口提供自定义页面：
 * [getTitle] 返回节点名，[buildComponent] 返回 Swing 组件，[getSubGroups] 可选子节点，
 * [close] 在关闭设置窗口时回调。
 *
 * **为什么保持 Java 可实现**：实现类都在 kadx-gui（Java）；[getSubGroups] / [close]
 * 是带默认实现的接口方法，Kotlin 会生成真正的 JVM default 方法，Java 实现方可选择不覆写。
 */
interface ISettingsGroup {

	/**
	 * 节点名称。
	 */
	fun getTitle(): String

	/**
	 * 自定义页面组件。
	 */
	fun buildComponent(): JComponent

	/**
	 * 可选的子节点列表。
	 */
	fun getSubGroups(): List<ISettingsGroup> = Collections.emptyList()

	/**
	 * 设置关闭时的回调。
	 * `save` 为 true 表示应用设置；也可用于清理资源。
	 */
	fun close(save: Boolean) {
		// optional method
	}
}
