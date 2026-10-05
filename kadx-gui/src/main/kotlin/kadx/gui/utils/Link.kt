package kadx.gui.utils

import kadx.commons.app.KadxSystemInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Desktop.Action
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JTextArea

/**
 * 可点击的超链接标签（继承 [JLabel]）。
 *
 * **做什么**：点击时优先用系统默认浏览器打开 URL，失败则尝试各平台命令，
 * 最后回退到弹窗展示 URL 让用户手动复制。
 *
 * **Swing 说明**：鼠标监听仍是原样的匿名 [MouseAdapter]，未做任何线程模型改动。
 */
class Link : JLabel {

	/** 要打开的 URL；无参构造后需通过 [setUrl] 设置。 */
	private var url: String? = null

	constructor() : super() {
		init()
	}

	constructor(text: String, url: String) : super(text) {
		init()
		setUrl(url)
	}

	private fun init() {
		cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
		addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				browse()
			}
		})
	}

	fun setUrl(url: String) {
		this.url = url
		toolTipText = "Open $url in your browser"
	}

	private fun browse() {
		val url = this.url ?: run {
			// URL 尚未设置：直接走弹窗提示（与原 Java 最终兜底行为一致）
			showUrlDialog()
			return
		}
		if (Desktop.isDesktopSupported()) {
			val desktop = Desktop.getDesktop()
			if (desktop.isSupported(Action.BROWSE)) {
				try {
					desktop.browse(java.net.URI(url))
					return
				} catch (e: Exception) {
					LOG.debug("Open url error", e)
				}
			}
		}
		try {
			if (KadxSystemInfo.IS_WINDOWS) {
				ProcessBuilder()
					.command("rundll32", "url.dll,FileProtocolHandler", url)
					.start()
				return
			}
			if (KadxSystemInfo.IS_MAC) {
				ProcessBuilder()
					.command("open", url)
					.start()
				return
			}
			val env = System.getenv()
			val browser = env["BROWSER"]
			if (browser != null) {
				ProcessBuilder()
					.command(browser, url)
					.start()
				return
			}
		} catch (e: Exception) {
			LOG.debug("Open url error", e)
		}
		showUrlDialog()
	}

	private fun showUrlDialog() {
		val urlArea = JTextArea("Can't open browser. Please browse to:\n$url")
		JOptionPane.showMessageDialog(null, urlArea)
	}

	companion object {
		private const val serialVersionUID = 3655322136444908178L

		private val LOG: Logger = LoggerFactory.getLogger(Link::class.java)
	}
}
