package kadx.gui.ui.panel

import hu.kazocsaba.imageviewer.ImageViewer
import kadx.api.ResourcesLoader
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.xmlgen.ResContainer
import kadx.gui.treemodel.JResource
import kadx.gui.ui.codearea.AbstractCodeArea
import kadx.gui.ui.tab.TabbedPane
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * 图片资源预览面板。
 *
 * **做什么**：把图片资源解码为 [BufferedImage]，交给 `ImageViewer` 展示；
 * 解码失败时退化为一个显示错误堆栈的文本区。
 *
 * **线程模型**：全部在 EDT 上执行。
 */
class ImagePanel(panel: TabbedPane, res: JResource) : ContentPanel(panel, res) {

	init {
		layout = BorderLayout()
		try {
			val img = loadImage(res)
			val imageViewer = ImageViewer(img)
			add(imageViewer.getComponent())
		} catch (e: Exception) {
			val textArea = AbstractCodeArea.getDefaultArea(panel.getMainWindow())
			textArea.setText("Image load error:\n" + Utils.getStackTrace(e))
			add(textArea)
		}
	}

	/** 加载图片；[ImageIO.read] 可能返回 `null`（无法识别的格式），与原 Java 语义一致。 */
	private fun loadImage(res: JResource): BufferedImage? {
		val resFile = checkNotNull(res.getResFile())
		val resContainer = resFile.loadContent()
		val dataType = resContainer.dataType
		if (dataType == ResContainer.DataType.DECODED_DATA) {
			try {
				return ImageIO.read(ByteArrayInputStream(resContainer.decodedData))
			} catch (e: Exception) {
				throw KadxRuntimeException("Failed to load image", e)
			}
		} else if (dataType == ResContainer.DataType.RES_LINK) {
			try {
				return ResourcesLoader.decodeStream(resFile) { _, inputStream -> ImageIO.read(inputStream) }
			} catch (e: Exception) {
				throw KadxRuntimeException("Failed to load image", e)
			}
		} else {
			throw KadxRuntimeException("Unsupported resource image data type: $resFile")
		}
	}

	override fun loadSettings() {
		// no op
	}
}
