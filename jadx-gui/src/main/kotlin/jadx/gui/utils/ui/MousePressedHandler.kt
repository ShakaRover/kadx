package jadx.gui.utils.ui

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer

/**
 * 只处理“鼠标按下”事件的 [MouseAdapter]。
 *
 * **做什么**：把 `mousePressed` 事件转发给构造时传入的 [Consumer]。
 *
 * **为什么用 Java 的 [Consumer]**：保留 Kotlin/Java 两侧的 SAM 用法
 * （`MousePressedHandler { ev -> ... }`）。
 */
class MousePressedHandler(private val listener: Consumer<MouseEvent>) : MouseAdapter() {

	override fun mousePressed(ev: MouseEvent) {
		listener.accept(ev)
	}
}
