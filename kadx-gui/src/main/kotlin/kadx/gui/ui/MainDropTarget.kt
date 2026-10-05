package kadx.gui.ui

import kadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.io.File

/**
 * 主窗口的拖放目标：支持从外部应用拖入 APK 等文件。
 *
 * **做什么**：拖拽经过时只接受“文件列表”类型；放下时把文件路径交给 [MainWindow.open]。
 *
 * **为什么保留 AWT `DropTargetListener` 覆写**：拖放事件由 AWT 直接回调，
 * 方法签名必须与原来一致。
 */
class MainDropTarget(private val mainWindow: MainWindow) : DropTargetListener {

	private fun processDrag(dtde: DropTargetDragEvent) {
		if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			dtde.acceptDrag(DnDConstants.ACTION_COPY)
		} else {
			dtde.rejectDrag()
		}
	}

	override fun dragEnter(dtde: DropTargetDragEvent) {
		processDrag(dtde)
	}

	override fun dragOver(dtde: DropTargetDragEvent) {
		processDrag(dtde)
	}

	override fun dropActionChanged(dtde: DropTargetDragEvent) {
	}

	@Suppress("UNCHECKED_CAST")
	override fun drop(dtde: DropTargetDropEvent) {
		if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			dtde.rejectDrop()
			return
		}
		dtde.acceptDrop(dtde.dropAction)
		try {
			val transferable: Transferable = dtde.transferable
			val transferData = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
			if (transferData.isNotEmpty()) {
				dtde.dropComplete(true)
				mainWindow.open(FileUtils.toPaths(transferData))
			}
		} catch (e: Exception) {
			LOG.error("File drop operation failed", e)
		}
	}

	override fun dragExit(dte: DropTargetEvent) {
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(MainDropTarget::class.java)
	}
}
