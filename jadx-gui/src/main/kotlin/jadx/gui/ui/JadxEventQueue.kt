package jadx.gui.ui

import jadx.commons.app.JadxSystemInfo
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

/**
 * 自定义 AWT 事件队列，修正 X11 下鼠标按键事件。
 *
 * **做什么**：在 Linux 的 XToolkit 上拦截鼠标事件：把触摸板产生的 4/5 号键转换为
 * 水平滚动（`MouseWheelEvent`），把 6/7 号键“平移”为 4/5 号键。
 *
 * **为什么保留 `EventQueue` 覆写**：这是 Swing 事件分发链路的一部分，`dispatchEvent`
 * 的覆写与 `push` 的注册方式必须与原实现完全一致。
 */
class JadxEventQueue private constructor() : EventQueue() {

	override fun dispatchEvent(event: AWTEvent) {
		val mappedEvent = mapEvent(event)
		super.dispatchEvent(mappedEvent)
	}

	companion object {
		private val IS_X_TOOLKIT: Boolean = JadxSystemInfo.IS_LINUX &&
			"sun.awt.X11.XToolkit" == Toolkit.getDefaultToolkit().javaClass.name

		@JvmStatic
		fun register() {
			if (IS_X_TOOLKIT) {
				Toolkit.getDefaultToolkit().systemEventQueue.push(JadxEventQueue())
			}
		}

		private fun mapEvent(event: AWTEvent): AWTEvent {
			if (IS_X_TOOLKIT && event is MouseEvent && event.getButton() > 3) {
				return mapXWindowMouseEvent(event)
			}
			return event
		}

		@Suppress("DEPRECATION")
		private fun mapXWindowMouseEvent(src: MouseEvent): AWTEvent = if (src.getButton() < 6) {
			// 4/5 号键来自触摸板，转换为水平滚动事件
			val modifiers = src.getModifiers() or InputEvent.SHIFT_DOWN_MASK
			MouseWheelEvent(
				src.getComponent(), MouseEvent.MOUSE_WHEEL, src.getWhen(), modifiers,
				src.getX(), src.getY(), 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
				src.getClickCount(), if (src.getButton() == 4) -1 else 1,
			)
		} else {
			// 把 6/7 号键“平移”为 4/5 号键：
			// 参见 `java.awt.InputEvent#BUTTON_DOWN_MASK`，1<<14 是第 4 个物理键，1<<15 是第 5 个
			val modifiers = src.getModifiers() or (1 shl (8 + src.getButton()))
			MouseEvent(
				src.getComponent(), src.getID(), src.getWhen(), modifiers,
				src.getX(), src.getY(), 1, src.isPopupTrigger(), src.getButton() - 2,
			)
		}
	}
}
