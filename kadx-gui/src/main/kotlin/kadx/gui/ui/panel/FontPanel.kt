package kadx.gui.ui.panel

import kadx.api.ResourceFile
import kadx.api.ResourcesLoader
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.xmlgen.ResContainer
import kadx.gui.treemodel.JResource
import kadx.gui.ui.codearea.AbstractCodeArea
import kadx.gui.ui.tab.TabbedPane
import kadx.gui.utils.NLS
import java.awt.BorderLayout
import java.awt.Font
import java.awt.FontFormatException
import java.io.ByteArrayInputStream

/**
 * 字体资源预览面板。
 *
 * **做什么**：把 `.ttf`/`.otf` 等字体资源加载为 [Font]，并用一段固定文本预览效果；
 * 若字体不支持预览字符，则显示本地化的提示信息。
 *
 * **线程模型**：全部在 EDT 上执行，保持原 Swing 模型。
 */
class FontPanel(panel: TabbedPane, res: JResource) : ContentPanel(panel, res) {

	init {
		layout = BorderLayout()
		val textArea = AbstractCodeArea.getDefaultArea(panel.getMainWindow())
		add(textArea, BorderLayout.CENTER)
		try {
			val selectedFont = loadFont(res)
			if (selectedFont.canDisplay(DEFAULT_PREVIEW_STRING.codePointAt(0))) {
				textArea.setFont(selectedFont)
				textArea.setText(DEFAULT_PREVIEW_STRING)
			} else {
				textArea.setText(NLS.str("message.unable_preview_font"))
			}
		} catch (e: Exception) {
			textArea.setText("Font load error:\n" + Utils.getStackTrace(e))
		}
	}

	/** 从资源文件加载字体；解码失败统一包装为 [KadxRuntimeException]。 */
	private fun loadFont(res: JResource): Font {
		val resFile: ResourceFile = checkNotNull(res.getResFile())
		val resContainer = resFile.loadContent()
		val dataType = resContainer.dataType
		return if (dataType == ResContainer.DataType.DECODED_DATA) {
			try {
				Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(resContainer.decodedData)).deriveFont(12f)
			} catch (e: Exception) {
				throw KadxRuntimeException("Failed to load font", e)
			}
		} else if (dataType == ResContainer.DataType.RES_LINK) {
			try {
				ResourcesLoader.decodeStream(resFile) { _, inputStream ->
					try {
						Font.createFont(Font.TRUETYPE_FONT, inputStream).deriveFont(12f)
					} catch (e: FontFormatException) {
						throw KadxRuntimeException("Failed to load font", e)
					}
				}
			} catch (e: Exception) {
				throw KadxRuntimeException("Failed to load font", e)
			}
		} else {
			throw KadxRuntimeException("Unsupported resource font data type: $resFile")
		}
	}

	override fun loadSettings() {
		// no op
	}

	companion object {
		private const val DEFAULT_PREVIEW_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n" +
			"abcdefghijklmnopqrstuvwxyz\n" +
			"1234567890!@#$%^&*()_-=+[]{}<,.>"
	}
}
