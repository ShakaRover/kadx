package jadx.gui.ui.tab.dnd

import java.awt.Point
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.DragGestureListener
import java.awt.dnd.DragSource
import java.awt.dnd.InvalidDnDOperationException

/**
 * 拖拽手势监听器：在识别到拖拽手势后启动一次标签页拖拽。
 *
 * **子类扩展点**：[getDragOrigin] 允许子类把拖拽起点从组件坐标转换为
 * TabbedPane 坐标（`TabComponent` 就依赖这一点）。
 */
open class TabDndGestureListener(private val dnd: TabDndController) : DragGestureListener {

	override fun dragGestureRecognized(e: DragGestureEvent) {
		val tabPt = getDragOrigin(e)
		if (!dnd.onStartDrag(tabPt)) {
			return
		}
		try {
			e.startDrag(DragSource.DefaultMoveDrop, TabDndTransferable(), TabDndSourceListener(dnd))
		} catch (ex: InvalidDnDOperationException) {
			throw IllegalStateException(ex)
		}
	}

	protected open fun getDragOrigin(e: DragGestureEvent): Point = e.getDragOrigin()
}
