package jadx.gui.settings.ui.font

import org.drjekyll.fontchooser.FontChooser
import org.drjekyll.fontchooser.panes.FamilyPane
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 * 对 [FontChooser] 内部私有组件的「反射式」微调工具。
 *
 * **做什么**：字体选择器库没有公开 API 来强制「仅等宽字体」或隐藏预览面板，
 * 因此这里通过反射访问其私有字段实现。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 全是静态方法，这样 Java / Kotlin
 * 两侧都能继续按 `FontChooserHack.hidePreview(...)` 调用。
 */
object FontChooserHack {

	private val LOG: Logger = LoggerFactory.getLogger(FontChooserHack::class.java)

	@JvmStatic
	fun setOnlyMonospace(fontChooser: FontChooser) {
		try {
			val familyPane = getPrivateField(fontChooser, "familyPane") as FamilyPane
			val monospacedCheckBox = getPrivateField(familyPane, "monospacedCheckBox") as JCheckBox
			monospacedCheckBox.isSelected = true
			monospacedCheckBox.isEnabled = false
		} catch (e: Throwable) {
			LOG.debug("Failed to set only monospace check box", e)
		}
	}

	@JvmStatic
	fun hidePreview(fontChooser: FontChooser) {
		try {
			val previewPanel = getPrivateField(fontChooser, "previewPanel") as JPanel
			previewPanel.isVisible = false
		} catch (e: Throwable) {
			LOG.debug("Failed to hide preview panel", e)
		}
	}

	@Throws(NoSuchFieldException::class, IllegalAccessException::class)
	private fun getPrivateField(obj: Any, fieldName: String): Any {
		val f = obj.javaClass.getDeclaredField(fieldName)
		f.isAccessible = true
		return f.get(obj)
	}
}
