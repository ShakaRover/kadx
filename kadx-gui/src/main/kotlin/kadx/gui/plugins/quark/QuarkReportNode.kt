package kadx.gui.plugins.quark

import kadx.api.ICodeInfo
import kadx.api.impl.SimpleCodeInfo
import kadx.core.utils.GsonUtils
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JNode
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.panel.HtmlPanel
import kadx.gui.ui.tab.TabbedPane
import kadx.gui.utils.UiUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Quark 分析报告树节点。
 *
 * **做什么**：指向 Quark 引擎生成的 JSON 报告文件；打开时解析并展示
 * [QuarkReportPanel]，解析失败则退化为展示错误堆栈的 [HtmlPanel]。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class QuarkReportNode(private val reportFile: Path) : JNode() {

	private var errorContent: ICodeInfo = ICodeInfo.EMPTY

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon = ICON

	override fun makeString(): String = "Quark analysis report"

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel = try {
		val data: QuarkReportData = Files.newBufferedReader(reportFile).use { reader: BufferedReader ->
			GsonUtils.buildGson().fromJson(reader, QuarkReportData::class.java)
		}
		data.validate()
		QuarkReportPanel(tabbedPane, this, data)
	} catch (e: Exception) {
		LOG.error("Quark report parse error", e)
		val builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4)
		builder.append("<h2>")
		builder.escape("Quark analysis failed!")
		builder.append("</h2>")
		builder.append("<h3>")
		builder.append("Error: ").escape(e.message)
		builder.append("</h3>")
		builder.append("<pre>")
		builder.escape(ExceptionUtils.getStackTrace(e))
		builder.append("</pre>")
		errorContent = SimpleCodeInfo(builder.toString())
		HtmlPanel(tabbedPane, this)
	}

	override fun getCodeInfo(): ICodeInfo = errorContent

	companion object {
		private const val serialVersionUID = -766800957202637021L

		private val LOG = LoggerFactory.getLogger(QuarkReportNode::class.java)

		private val ICON: ImageIcon = UiUtils.openSvgIcon("ui/quark")
	}
}
