package jadx.gui.ui.treenodes

import jadx.api.ICodeInfo
import jadx.api.ResourceFile
import jadx.api.impl.SimpleCodeInfo
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.ProcessState
import jadx.core.utils.ErrorsCounter
import jadx.core.utils.Utils
import jadx.gui.JadxWrapper
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.panel.HtmlPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.UiUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import java.io.File
import java.io.IOException
import java.util.Comparator
import java.util.HashMap
import java.util.HashSet
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * “汇总”节点：以 HTML 形式展示本次加载/反编译的统计信息。
 *
 * **做什么**：输入文件、代码来源、native 库统计，以及各类处理状态、错误/警告计数与
 * 方法成功率，最终生成一段 HTML 供 [HtmlPanel] 显示。
 */
class SummaryNode(mainWindow: MainWindow) : JNode() {

	private val mainWindow: MainWindow = mainWindow
	private val wrapper: JadxWrapper = mainWindow.getWrapper()

	override fun getCodeInfo(): ICodeInfo {
		val builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4)
		try {
			builder.append("<html>")
			builder.append("<body>")
			writeInputSummary(builder)
			writeDecompilationSummary(builder)
			builder.append("</body>")
		} catch (e: Exception) {
			builder.append("Error build summary: ")
			builder.append("<pre>")
			builder.escape(Utils.getStackTrace(e))
			builder.append("</pre>")
		}
		return SimpleCodeInfo(builder.toString())
	}

	private fun writeInputSummary(builder: StringEscapeUtils.Builder) {
		builder.append("<h2>Input</h2>")
		builder.append("<h3>Files</h3>")
		builder.append("<ul>")
		for (inputFile in wrapper.args.inputFiles) {
			builder.append("<li>")
			builder.escape(inputFile.getCanonicalFile().getAbsolutePath())
			builder.append("</li>")
		}
		builder.append("</ul>")

		val classes = wrapper.rootNode.getClasses(true)
		val codeSources = classes
			.map { it.inputFileName ?: "" }
			.distinct()
			.sorted()
			.toMutableList()
		codeSources.remove("synthetic")
		val codeSourcesCount = codeSources.size
		builder.append("<h3>Code sources</h3>")
		builder.append("<ul>")
		if (codeSourcesCount != 1) {
			builder.append("<li>Count: " + codeSourcesCount + "</li>")
		}
		for (input in codeSources) {
			builder.append("<li>")
			builder.escape(input)
			builder.append("</li>")
		}
		builder.append("</ul>")

		addNativeLibsInfo(builder)

		val methodsCount = classes.sumOf { cls -> cls.methods.size }
		val fieldsCount = classes.sumOf { cls -> cls.fields.size }
		val insnCount = classes.sumOf { cls -> cls.methods.sumOf { it.insnsCount } }
		builder.append("<h3>Counts</h3>")
		builder.append("<ul>")
		builder.append("<li>Classes: " + classes.size + "</li>")
		builder.append("<li>Methods: " + methodsCount + "</li>")
		builder.append("<li>Fields: " + fieldsCount + "</li>")
		builder.append("<li>Instructions: " + insnCount + " (units)</li>")
		builder.append("</ul>")
	}

	private fun addNativeLibsInfo(builder: StringEscapeUtils.Builder) {
		val nativeLibs = wrapper.resources
			.map { it.getOriginalName() }
			.filter { f -> f.endsWith(".so") }
			.sorted()
		builder.append("<h3>Native libs</h3>")
		builder.append("<ul>")
		if (nativeLibs.isEmpty()) {
			builder.append("<li>Total count: 0</li>")
		} else {
			val libsByArch = HashMap<String, MutableSet<String>>()
			for (libFile in nativeLibs) {
				val parts = StringUtils.split(libFile, '/')
				val count = parts.size
				if (count >= 2) {
					val arch = parts[count - 2]
					val name = parts[count - 1]
					libsByArch.computeIfAbsent(arch) { HashSet() }.add(name)
				}
			}
			val arches = libsByArch.keys
				.sorted()
				.joinToString(", ")
			builder.append("<li>Arch list: ")
			builder.escape(arches)
			builder.append("</li>")

			val perArchCount = libsByArch.entries
				.map { entry -> entry.key + ":" + entry.value.size }
				.sorted()
				.joinToString(", ")
			builder.append("<li>Per arch count: ")
			builder.escape(perArchCount)
			builder.append("</li>")

			builder.append("<br>")
			builder.append("<li>Total count: " + nativeLibs.size + "</li>")
			for (lib in nativeLibs) {
				builder.append("<li>")
				builder.escape(lib)
				builder.append("</li>")
			}
		}
		builder.append("</ul>")
	}

	private fun writeDecompilationSummary(builder: StringEscapeUtils.Builder) {
		builder.append("<h2>Decompilation</h2>")
		val classes = wrapper.rootNode.classesWithoutInner
		val classesCount = classes.size
		val notLoadedClasses = classes.count { c -> c.state === ProcessState.NOT_LOADED }
		val loadedClasses = classes.count { c -> c.state === ProcessState.LOADED }
		val processedClasses = classes.count { c -> c.state === ProcessState.PROCESS_COMPLETE }
		val generatedClasses = classes.count { c -> c.state === ProcessState.GENERATED_AND_UNLOADED }
		builder.append("<ul>")
		builder.append("<li>Top level classes: " + classesCount + "</li>")
		builder.append("<li>Not loaded: " + valueAndPercent(notLoadedClasses, classesCount) + "</li>")
		builder.append("<li>Loaded: " + valueAndPercent(loadedClasses, classesCount) + "</li>")
		builder.append("<li>Processed: " + valueAndPercent(processedClasses, classesCount) + "</li>")
		builder.append("<li>Code generated: " + valueAndPercent(generatedClasses, classesCount) + "</li>")
		builder.append("</ul>")

		val counter: ErrorsCounter = wrapper.rootNode.errorsCounter
		val problemNodes = HashSet<IAttributeNode>()
		problemNodes.addAll(counter.errorNodes)
		problemNodes.addAll(counter.warnNodes)
		val problemMethods = problemNodes.count { it is MethodNode }
		val methodsCount = classes.sumOf { cls -> cls.methods.size }
		val methodSuccessRate = (methodsCount - problemMethods) * 100.0 / methodsCount.toDouble()

		builder.append("<h3>Issues</h3>")
		builder.append("<ul>")
		builder.append("<li>Errors: " + counter.errorCount + "</li>")
		builder.append("<li>Warnings: " + counter.getWarnsCount() + "</li>")
		builder.append("<li>Nodes with errors: " + counter.errorNodes.size + "</li>")
		builder.append("<li>Nodes with warnings: " + counter.warnNodes.size + "</li>")
		builder.append("<li>Total nodes with issues: " + problemNodes.size + "</li>")
		builder.append("<li>Methods with issues: " + problemMethods + "</li>")
		builder.append("<li>Methods success rate: " + String.format("%.2f", methodSuccessRate) + "%</li>")
		builder.append("</ul>")
	}

	private fun valueAndPercent(value: Int, total: Int): String = String.format("%d (%.2f%%)", value, value * 100 / total.toDouble())

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel = HtmlPanel(tabbedPane, this)

	override fun makeString(): String = "Summary"

	override fun getIcon(): Icon = ICON

	override fun getJParent(): JClass? = null

	companion object {
		private const val serialVersionUID: Long = 4295299814582784805L

		private val ICON: ImageIcon = UiUtils.openSvgIcon("nodes/detailView")
	}
}
