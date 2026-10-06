package kadx.gui.utils.ui

import kadx.gui.settings.KadxSettings
import kadx.gui.settings.font.FontAdapter
import kadx.gui.ui.codearea.SmaliArea
import kadx.gui.utils.UiUtils
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.ActionMap
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.KeyStroke

/**
 * 为代码区/文本区注册缩放（Ctrl + +/- 与 Ctrl + 滚轮）动作。
 *
 * **做什么**：向组件的 InputMap/ActionMap 注册放大、缩小动作，并监听鼠标滚轮；
 * 缩放会更新对应字体设置并触发 [update] 回调。
 */
class ZoomActions private constructor(
	private val component: JComponent,
	private val settings: KadxSettings,
	private val update: Runnable,
) {

	private fun register() {
		val zoomIn = "TextZoomIn"
		val zoomOut = "TextZoomOut"
		val ctrlButton = UiUtils.ctrlButton()
		val inputMap: InputMap = component.getInputMap()
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrlButton), zoomIn)
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrlButton), zoomIn)
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrlButton), zoomIn)
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrlButton), zoomOut)
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrlButton), zoomOut)
		val actionMap: ActionMap = component.getActionMap()
		actionMap.put(zoomIn, ActionHandler(Runnable { textZoom(1) }))
		actionMap.put(zoomOut, ActionHandler(Runnable { textZoom(-1) }))

		val zoomReset = "TextZoomReset"
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, ctrlButton), zoomReset)
		actionMap.put(zoomReset, ActionHandler(Runnable { textReset() }))

		component.addMouseWheelListener { e ->
			if (e.getModifiersEx() and UiUtils.ctrlButton() != 0) {
				// Ctrl+滚轮：缩放字号并消费事件
				textZoom(if (e.getWheelRotation() < 0) 1 else -1)
				e.consume()
			} else {
				// 普通滚轮：转发给最近的 JScrollPane 滚动内容。
				// AWT 不会自动把滚轮事件冒泡给带滚轮监听器的祖先，必须手动转发；
				// 转发后消费本事件，避免 AWT 父链冒泡造成重复滚动
				val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, component)
				if (scrollPane != null) {
					scrollPane.dispatchEvent(e)
					e.consume()
				}
			}
		}
	}

	private fun textZoom(change: Int) {
		val fontSettings = settings.getFontSettings()
		val fontAdapter: FontAdapter
		if (component is SmaliArea) {
			fontAdapter = fontSettings.getSmaliFontAdapter()
		} else {
			fontAdapter = fontSettings.getCodeFontAdapter()
		}
		fontAdapter.setFont(changeFontSize(fontAdapter.getFont(), change))

		settings.sync()
		update.run()
	}

	private fun textReset() {
		val fontSettings = settings.getFontSettings()
		val fontAdapter: FontAdapter = if (component is SmaliArea) {
			fontSettings.getSmaliFontAdapter()
		} else {
			fontSettings.getCodeFontAdapter()
		}
		fontAdapter.setFont(null) // 恢复默认字号
		settings.sync()
		update.run()
	}

	private fun changeFontSize(font: Font, change: Int): Font {
		val newSize = font.getSize() + change
		if (newSize < 2) {
			// 字号过小则忽略本次修改
			return font
		}
		return font.deriveFont(newSize.toFloat())
	}

	companion object {
		/** 为组件注册缩放动作。 */
		fun register(component: JComponent, settings: KadxSettings, update: Runnable) {
			val actions = ZoomActions(component, settings, update)
			actions.register()
		}
	}
}
