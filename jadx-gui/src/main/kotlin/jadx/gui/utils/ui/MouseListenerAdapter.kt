package jadx.gui.utils.ui

import java.awt.event.MouseEvent
import java.awt.event.MouseListener

/**
 * [MouseListener] 的空实现基类。
 *
 * **做什么**：为只需要覆写个别方法的调用方提供默认空实现，
 * 等价于 Java 的 `new MouseListener() { ... }` 便捷写法。
 *
 * **为什么是 `abstract`**：原 Java 类即为抽象类；`override` 方法默认开放，
 * 允许 Java 匿名子类继续覆写。
 */
abstract class MouseListenerAdapter : MouseListener {

	override fun mouseClicked(e: MouseEvent) {
	}

	override fun mousePressed(e: MouseEvent) {
	}

	override fun mouseReleased(e: MouseEvent) {
	}

	override fun mouseEntered(e: MouseEvent) {
	}

	override fun mouseExited(e: MouseEvent) {
	}
}
