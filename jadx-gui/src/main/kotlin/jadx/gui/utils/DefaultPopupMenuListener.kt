package jadx.gui.utils

import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * [PopupMenuListener] 的空实现（所有回调默认什么都不做）。
 *
 * **做什么**：方便子类只覆写自己关心的那一个弹出菜单回调，其余保持无操作。
 *
 * **为什么要保留为 Java 可实现的接口**：原 Java 中多个类实现该接口，
 * 迁移后仍需 Java 代码可实现，因此保持普通接口 + default 方法形态。
 */
interface DefaultPopupMenuListener : PopupMenuListener {

	override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
		// 默认不处理
	}

	override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
		// 默认不处理
	}

	override fun popupMenuCanceled(e: PopupMenuEvent) {
		// 默认不处理
	}
}
