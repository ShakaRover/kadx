package jadx.gui.utils.ui

import jadx.gui.settings.JadxSettings
import jadx.gui.settings.font.FontAdapter
import jadx.gui.ui.codearea.SmaliArea
import jadx.gui.utils.UiUtils
import java.awt.Container
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.util.function.Consumer
import javax.swing.ActionMap
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * 为代码区/文本区注册缩放（Ctrl + +/- 与 Ctrl + 滚轮）动作。
 *
 * **做什么**：向组件的 InputMap/ActionMap 注册放大、缩小动作，并监听鼠标滚轮；
 * 缩放会更新对应字体设置并触发 [update] 回调。
 */
class ZoomActions private constructor(
	private val component: JComponent,
	private val settings: JadxSettings,
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
		actionMap.put(zoomIn, ActionHandler(Consumer<ActionEvent> { textZoom(1) }))
		actionMap.put(zoomOut, ActionHandler(Consumer<ActionEvent> { textZoom(-1) }))

		component.addMouseWheelListener { e ->
			if (e.getModifiersEx() == UiUtils.ctrlButton()) {
				textZoom(if (e.getWheelRotation() < 0) 1 else -1)
				e.consume()
			} else {
				// 把事件转发给父组件，保证 JScrollPane 仍能滚动
				val parent: Container? = component.getParent()
				parent?.dispatchEvent(e)
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
		@JvmStatic
		fun register(component: JComponent, settings: JadxSettings, update: Runnable) {
			val actions = ZoomActions(component, settings, update)
			actions.register()
		}
	}
}
