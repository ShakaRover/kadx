package jadx.gui.ui.codearea.theme

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea

/**
 * 代码编辑器主题接口。
 *
 * **做什么**：统一描述一个主题的「唯一 ID、显示名、加载、应用、卸载」五个动作。
 * 内置 RSTA 主题、磁盘 XML 主题、动态主题等都实现此接口。
 *
 * **Kotlin 转换说明**：接口属性在 JVM 上仍生成 `getId()` / `getName()`，
 * Java 实现方与调用方（如 `theme.getId()`）零改动。
 */
interface IEditorTheme {

	/** 主题唯一标识（会写入配置持久化）。 */
	val id: String

	/** 主题显示名（展示在设置界面的下拉框中）。 */
	val name: String

	/** 可选的加载动作，默认什么都不做。 */
	fun load() {
		// 可选方法，默认空实现
	}

	/** 把当前主题的配色应用到指定的代码编辑器。 */
	fun apply(textArea: RSyntaxTextArea)

	/** 可选的卸载动作，默认什么都不做。 */
	fun unload() {
		// 可选方法，默认空实现
	}
}
