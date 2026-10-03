package jadx.gui.ui.tab.dnd

import java.awt.Component
import java.awt.dnd.DragSource
import java.awt.dnd.DragSourceDragEvent
import java.awt.dnd.DragSourceDropEvent
import java.awt.dnd.DragSourceEvent
import java.awt.dnd.DragSourceListener
import javax.swing.JComponent

/**
 * 拖拽源监听器：维护拖拽光标，并在拖拽结束时隐藏 glass pane。
 *
 * **线程模型**：AWT 拖拽事件在 EDT 上派发，这里保持同步处理。
 */
class TabDndSourceListener(private val dnd: TabDndController) : DragSourceListener {

	override fun dragEnter(e: DragSourceDragEvent) {
		e.getDragSourceContext().setCursor(DragSource.DefaultMoveDrop)
	}

	override fun dragExit(e: DragSourceEvent) {
		e.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop)
	}

	override fun dragOver(e: DragSourceDragEvent) {
	}

	override fun dragDropEnd(e: DragSourceDropEvent) {
		dnd.setDragging(false)
		val c = e.getDragSourceContext().getComponent()
		if (c is JComponent) {
			val rp = c.getRootPane()
			if (rp.getGlassPane() != null) {
				rp.getGlassPane().setVisible(false)
			}
		}
	}

	override fun dropActionChanged(e: DragSourceDragEvent) {
	}
}
