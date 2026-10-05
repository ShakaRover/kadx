package kadx.gui.ui.tab.dnd

import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener

/**
 * 拖拽目标监听器：在幽灵面板上跟踪光标、显示插入标记，并在释放时交换标签顺序。
 */
class TabDndTargetListener(private val dnd: TabDndController) : DropTargetListener {

	override fun dragEnter(e: DropTargetDragEvent) {
		val pane = dnd.dndGhostPane
		if (pane == null || e.getDropTargetContext().getComponent() !== pane) {
			return
		}
		val t = e.getTransferable()
		val f = e.getCurrentDataFlavors()
		if (t.isDataFlavorSupported(f[0])) {
			e.acceptDrag(e.getDropAction())
		} else {
			e.rejectDrag()
		}
	}

	override fun dragExit(e: DropTargetEvent) {
		val pane = dnd.dndGhostPane
		if (pane == null || e.getDropTargetContext().getComponent() !== pane) {
			return
		}
		pane.setPoint(HIDDEN_POINT)
		pane.setTargetRect(0, 0, 0, 0)
		pane.repaint()
	}

	override fun dropActionChanged(e: DropTargetDragEvent) {
	}

	override fun dragOver(e: DropTargetDragEvent) {
		val pane = dnd.dndGhostPane
		if (pane == null || e.getDropTargetContext().getComponent() !== pane) {
			return
		}
		val glassPt = e.getLocation()
		dnd.updateTargetMark(dnd.getTargetTabIndex(glassPt))
		dnd.scrollIfNeeded(glassPt) // 向左/向右滚动
		pane.setPoint(glassPt)
		pane.repaint()
	}

	override fun drop(e: DropTargetDropEvent) {
		val pane = dnd.dndGhostPane
		if (pane == null || e.getDropTargetContext().getComponent() !== pane) {
			return
		}
		val t = e.getTransferable()
		val f = t.getTransferDataFlavors()
		val oldIdx = dnd.dragTabIndex
		val newIdx = dnd.getTargetTabIndex(e.getLocation())
		if (t.isDataFlavorSupported(f[0]) && oldIdx != newIdx) {
			dnd.swapTabs(oldIdx, newIdx)
			e.dropComplete(true)
		} else {
			e.dropComplete(false)
		}
		pane.setVisible(false)
	}

	companion object {
		private val HIDDEN_POINT = Point(0, -1000)
	}
}
