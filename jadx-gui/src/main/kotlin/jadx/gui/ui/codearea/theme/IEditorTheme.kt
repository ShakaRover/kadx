package jadx.gui.ui.codearea.theme

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea

/**
 * 代码编辑器主题接口。
 *
 * **做什么**：统一描述一个主题的「唯一 ID、显示名、加载、应用、卸载」五个动作。
 * 内置 RSTA 主题、磁盘 XML 主题、动态主题等都实现此接口。
 *
 * **为什么用显式 `getId()` / `getName()` 函数而不是 Kotlin 属性**：
 * 该接口需要被 Java 代码实现与调用，显式函数能保证 JVM 签名与原来完全一致，
 * Java 侧调用 `theme.getId()` 零改动（Kotlin 属性无法被 Kotlin 调用方写成 `getId()`）。
 */
interface IEditorTheme {

	/** 主题唯一标识（会写入配置持久化）。 */
	fun getId(): String

	/** 主题显示名（展示在设置界面的下拉框中）。 */
	fun getName(): String

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
