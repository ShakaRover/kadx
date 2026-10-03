package jadx.gui.treemodel

import jadx.api.ICodeInfo
import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.ResourcesLoader
import jadx.api.impl.SimpleCodeInfo
import jadx.api.resources.ResourceContentType
import jadx.core.utils.Utils
import jadx.core.xmlgen.ResContainer
import jadx.gui.jobs.SimpleTask
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.panel.ResourcePanel
import jadx.gui.ui.popupmenu.JResourcePopupMenu
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.res.ResTableHelper
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Comparator
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

/**
 * 资源节点。
 *
 * **做什么**：资源树（`res/`、`assets/` 等）的节点，按 `ROOT` / `DIR` / `FILE` 三种类型组织。
 * 目录节点在展开时按需加载子节点；文件节点在打开时解析并缓存内容。
 *
 * **为什么不是 `data class`**：节点相等性由 `name` + `type` 决定，且树中存在父子互相引用。
 */
open class JResource : JLoadableNode {

	/** 资源节点类型。 */
	enum class JResType {
		ROOT,
		DIR,
		FILE,
	}

	private val name: String
	private val shortName: String
	private val type: JResType
	private val resFile: ResourceFile?

	@Volatile
	private var loaded: Boolean = false

	private var subNodes: MutableList<JResource> = ArrayList()
	private var content: ICodeInfo = ICodeInfo.EMPTY

	constructor(resFile: ResourceFile?, name: String, type: JResType) : this(resFile, name, name, type)

	constructor(resFile: ResourceFile?, name: String, shortName: String, type: JResType) : super() {
		if (resFile == null && type == JResType.FILE) {
			throw IllegalArgumentException("Null resource file")
		}
		this.resFile = resFile
		this.name = name
		this.shortName = shortName
		this.type = type
		this.loaded = false
	}

	@Synchronized
	fun update() {
		removeAllChildren()
		if (Utils.isEmpty(subNodes)) {
			if (type == JResType.DIR || type == JResType.ROOT || resFile?.getType() == ResourceType.ARSC) {
				// 放置一个假的叶子节点，强制显示展开按钮；
				// 真正的子节点会在展开时由 loadNode() 加载
				add(TextNode(NLS.str("tree.loading")))
			}
		} else {
			for (res in subNodes) {
				res.update()
				add(res)
			}
			if (type != JResType.FILE) {
				// 无内容可加载，直接标记为已加载
				loaded = true
			}
		}
	}

	@Synchronized
	override fun loadNode() {
		getCodeInfo()
		update()
	}

	@Synchronized
	override fun getLoadTask(): SimpleTask? {
		if (loaded) {
			return null
		}
		return SimpleTask(NLS.str("progress.load"), Runnable { getCodeInfo() }, Runnable { update() })
	}
	override fun getName(): String = name

	fun getShortName(): String = shortName

	fun getType(): JResType = type

	fun getSubNodes(): List<JResource> = subNodes

	fun addSubNode(node: JResource) {
		if (subNodes.isEmpty()) {
			subNodes = ArrayList(1)
		}
		subNodes.add(node)
	}

	fun sortSubNodes() {
		sortResNodes(subNodes)
	}

	private fun sortResNodes(nodes: MutableList<JResource>) {
		if (Utils.notEmpty(nodes)) {
			nodes.forEach { it.sortSubNodes() }
			nodes.sortWith(RESOURCES_COMPARATOR)
		}
	}

	/**
	 * 将只有单个子目录的 DIR 链合并为一个节点，并用 `/` 连接显示名（类似 GitHub 的目录展示）。
	 */
	companion object {
		private const val serialVersionUID = -201018424302612434L

		@JvmField
		val RESOURCES_COMPARATOR: Comparator<JResource> =
			Comparator
				.comparingInt<JResource> { res -> res.type.ordinal }
				.thenComparing({ res -> res.getName() }, String.CASE_INSENSITIVE_ORDER)

		@JvmStatic
		fun mergeMiddleDirs(root: JResource) {
			mergeChildren(root.subNodes)
		}

		@JvmStatic
		fun mergeMiddleDirs(roots: MutableList<JResource>) {
			mergeChildren(roots)
		}

		private fun mergeChildren(children: MutableList<JResource>) {
			for (i in children.indices) {
				val sub = children[i]
				val replaced = mergeChain(sub)
				if (replaced !== sub) {
					children[i] = replaced
				}
				mergeChildren(replaced.subNodes)
			}
		}

		private fun mergeChain(node: JResource): JResource {
			if (node.type != JResType.DIR) {
				return node
			}
			val merged = ArrayList<JResource>()
			var deepestDir = node
			var subs = deepestDir.subNodes
			while (subs.size == 1 && subs[0].type == JResType.DIR) {
				deepestDir = subs[0]
				merged.add(deepestDir)
				subs = deepestDir.subNodes
			}
			if (merged.isNotEmpty()) {
				// 把找到的单子目录链合并到当前节点
				merged.add(0, node)
				val shortName = merged.joinToString("/") { it.shortName }
				val name = merged.joinToString("/") { it.name }
				val mergedNode = JResource(node.resFile, name, shortName, JResType.DIR)
				mergedNode.subNodes = deepestDir.subNodes
				return mergedNode
			}
			return node
		}

		private val ROOT_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/resourcesRoot")
		private val ARSC_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/resourceBundle")
		private val XML_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/xml")
		private val IMAGE_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/ImagesFileType")
		private val SO_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/binaryFile")
		private val MANIFEST_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/manifest")
		private val JAVA_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/java")
		private val APK_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/archiveApk")
		private val AUDIO_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/audioFile")
		private val VIDEO_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/videoFile")
		private val FONT_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/fontFile")
		private val HTML_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/html")
		private val JSON_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/json")
		private val TEXT_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/text")
		private val ARCHIVE_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/archive")
		private val UNKNOWN_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/unknown")

		private val EXTENSION_TO_FILE_SYNTAX: Map<String, String> = Utils.newConstStringMap(
			"java", SyntaxConstants.SYNTAX_STYLE_JAVA,
			"smali", AbstractCodeArea.SYNTAX_STYLE_SMALI,
			"js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT,
			"ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT,
			"json", SyntaxConstants.SYNTAX_STYLE_JSON,
			"css", SyntaxConstants.SYNTAX_STYLE_CSS,
			"less", SyntaxConstants.SYNTAX_STYLE_LESS,
			"html", SyntaxConstants.SYNTAX_STYLE_HTML,
			"xml", SyntaxConstants.SYNTAX_STYLE_XML,
			"yaml", SyntaxConstants.SYNTAX_STYLE_YAML,
			"properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE,
			"ini", SyntaxConstants.SYNTAX_STYLE_INI,
			"sql", SyntaxConstants.SYNTAX_STYLE_SQL,
		)

		@JvmStatic
		fun isSupportedForView(type: ResourceType): Boolean = when (type) {
			ResourceType.SOUNDS,
			ResourceType.VIDEOS,
			ResourceType.ARCHIVE,
			ResourceType.APK,
			-> false

			else -> true
		}

		@JvmStatic
		fun isOpenInExternalTool(type: ResourceType): Boolean = when (type) {
			ResourceType.SOUNDS,
			ResourceType.VIDEOS,
			-> true

			else -> false
		}
	}
	override fun hasContent(): Boolean = resFile != null

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel? {
		if (resFile == null) {
			return null
		}
		return ResourcePanel(tabbedPane, this)
	}

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu = JResourcePopupMenu(mainWindow, this)

	@Synchronized
	override fun getCodeInfo(): ICodeInfo {
		if (loaded) {
			return content
		}
		val codeInfo = loadContent()
		content = codeInfo
		loaded = true
		return codeInfo
	}

	override fun getContentType(): ResourceContentType {
		if (type == JResType.FILE) {
			return checkNotNull(resFile).getType().contentType
		}
		return ResourceContentType.CONTENT_NONE
	}

	private fun loadContent(): ICodeInfo {
		val resFile = this.resFile
		if (resFile == null || type != JResType.FILE) {
			return ICodeInfo.EMPTY
		}
		val rc = resFile.loadContent()
		if (rc.dataType == ResContainer.DataType.RES_TABLE) {
			val codeInfo = loadCurrentSingleRes(rc)
			val nodes = ResTableHelper.buildTree(this, rc)
			sortResNodes(nodes)
			subNodes = nodes
			UiUtils.uiRun { update() }
			return codeInfo
		}
		// 单一资源节点
		return loadCurrentSingleRes(rc)
	}

	private fun loadCurrentSingleRes(rc: ResContainer): ICodeInfo {
		when (rc.dataType) {
			ResContainer.DataType.TEXT, ResContainer.DataType.RES_TABLE -> return rc.text

			ResContainer.DataType.RES_LINK -> {
				try {
					val resourceFile = rc.resLink
					return ResourcesLoader.decodeStream(resourceFile) { size, inputStream ->
						// TODO: 在加载前检查大小
						if (size > 10 * 1024 * 1024L) {
							SimpleCodeInfo("File too large for view")
						} else {
							val charset: Charset = if (resourceFile.getType().contentType == ResourceContentType.CONTENT_TEXT) {
								StandardCharsets.UTF_8
							} else {
								// 二进制数据强制使用单字节字符集，保证偏移与字节数组一致
								StandardCharsets.US_ASCII
							}
							ResourcesLoader.loadToCodeWriter(inputStream, charset)
						}
					}
				} catch (e: Exception) {
					return SimpleCodeInfo("Failed to load resource file:\n" + Utils.getStackTrace(e))
				}
			}

			else -> return SimpleCodeInfo("Unexpected resource type: " + rc)
		}
	}
	override fun getSyntaxName(): String? {
		val resFile = this.resFile ?: return null
		when (resFile.getType()) {
			ResourceType.CODE -> return super.getSyntaxName()

			ResourceType.MANIFEST, ResourceType.XML, ResourceType.ARSC -> return SyntaxConstants.SYNTAX_STYLE_XML

			else -> {
				val syntax = getSyntaxByExtension(resFile.getDeobfName())
				if (syntax != null) {
					return syntax
				}
				return super.getSyntaxName()
			}
		}
	}

	private fun getSyntaxByExtension(name: String): String? {
		val dot = name.lastIndexOf('.')
		if (dot == -1) {
			return null
		}
		val ext = name.substring(dot + 1)
		return EXTENSION_TO_FILE_SYNTAX[ext]
	}

	fun hasSyntaxByExtension(): Boolean = resFile != null && getSyntaxByExtension(checkNotNull(resFile).getDeobfName()) != null

	override fun getIcon(): Icon {
		when (type) {
			JResType.ROOT -> return ROOT_ICON

			JResType.DIR -> return Icons.FOLDER

			JResType.FILE -> {
				val resType: ResourceType = checkNotNull(resFile).getType()
				when (resType) {
					ResourceType.MANIFEST -> return MANIFEST_ICON
					ResourceType.ARSC -> return ARSC_ICON
					ResourceType.XML -> return XML_ICON
					ResourceType.IMG -> return IMAGE_ICON
					ResourceType.LIB -> return SO_ICON
					ResourceType.CODE -> return JAVA_ICON
					ResourceType.APK -> return APK_ICON
					ResourceType.VIDEOS -> return VIDEO_ICON
					ResourceType.SOUNDS -> return AUDIO_ICON
					ResourceType.FONT -> return FONT_ICON
					ResourceType.HTML -> return HTML_ICON
					ResourceType.JSON -> return JSON_ICON
					ResourceType.TEXT -> return TEXT_ICON
					ResourceType.ARCHIVE -> return ARCHIVE_ICON
					ResourceType.UNKNOWN -> return UNKNOWN_ICON
					else -> return UNKNOWN_ICON
				}
				return UNKNOWN_ICON
			}
		}
		return Icons.FILE
	}

	fun getResFile(): ResourceFile? = resFile

	override fun getJParent(): JClass? = null

	override fun getID(): String {
		if (type == JResType.ROOT) {
			return "JResources"
		}
		return makeString()
	}

	override fun makeString(): String = shortName

	override fun makeLongString(): String = name

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val otherRes = other as JResource
		return name == otherRes.name && type == otherRes.type
	}

	override fun hashCode(): Int = name.hashCode() + 31 * type.ordinal
}
