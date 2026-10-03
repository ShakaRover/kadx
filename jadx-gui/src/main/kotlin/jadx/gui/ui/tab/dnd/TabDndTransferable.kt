package jadx.gui.ui.tab.dnd

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/**
 * 标签页拖拽使用的本地传输对象。
 *
 * **做什么**：声明一个 JVM 本地对象 flavor，仅用于在同一个 JVM 内
 * 识别“这是一个标签页拖拽”，不携带真实数据。
 */
class TabDndTransferable : Transferable {

	override fun getTransferData(flavor: DataFlavor): Any = this

	override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor(DataFlavor.javaJVMLocalObjectMimeType, NAME))

	override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = NAME == flavor.getHumanPresentableName()

	companion object {
		private const val NAME = "Transferable Tab"
	}
}
