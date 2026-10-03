package jadx.gui.ui.panel

import jadx.gui.settings.JadxSettings
import jadx.gui.treemodel.JNode
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.ui.ZoomActions
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JEditorPane
import javax.swing.JScrollPane

/**
 * HTML 内容面板。
 *
 * **做什么**：把节点提供的 HTML 代码渲染到只读的 [JEditorPane] 中，
 * 并接入 [ZoomActions] 以支持缩放；主要用于 APK 签名信息、摘要等页面。
 *
 * **线程模型**：全部在 EDT 上执行。
 */
class HtmlPanel(panel: TabbedPane, jnode: JNode) : ContentPanel(panel, jnode) {

	private val textArea: JHtmlPane

	init {
		layout = BorderLayout()
		textArea = JHtmlPane()
		loadSettings()
		loadContent(jnode)
		textArea.setEditable(false)
		val sp = JScrollPane(textArea)
		add(sp)

		ZoomActions.register(textArea, panel.getMainWindow().getSettings(), Runnable { loadSettings() })
	}

	override fun loadSettings() {
		val settings: JadxSettings = getMainWindow().getSettings()
		textArea.setFont(settings.getUiFont())
	}

	/** 用 [jnode] 的 HTML 代码刷新内容，并把光标移到开头。 */
	fun loadContent(jnode: JNode) {
		textArea.setText(jnode.getCodeInfo().getCodeStr())
		textArea.setCaretPosition(0) // otherwise the start view will be the last line
	}

	/** 暴露内部 HTML 组件，供 `TabbedPane` 注册焦点监听。 */
	fun getHtmlArea(): JEditorPane = textArea

	/** 开启抗锯齿的 HTML 编辑器面板。 */
	private class JHtmlPane : JEditorPane() {

		init {
			setContentType("text/html")
		}

		override fun paint(g: Graphics) {
			val g2d = g.create() as Graphics2D
			try {
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
				super.paint(g2d)
			} finally {
				g2d.dispose()
			}
		}
	}
}
