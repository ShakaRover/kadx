package jadx.gui.treemodel

import jadx.api.ResourceFile
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.JadxWrapper
import jadx.gui.settings.JadxProject
import jadx.gui.treemodel.JResource.JResType
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import java.nio.file.Path
import java.util.regex.Pattern
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * 树的根节点。
 *
 * **做什么**：JRoot 之下依次挂载输入文件（[JInputs]）、源码（[JSources]）与资源（[JResource]）子树，
 * 并允许插件注册自定义节点（[customNodes]）。
 *
 * **为什么不是 `data class`**：它是整棵树的身份根节点，需要按引用比较。
 */
class JRoot(mainWindow: MainWindow) : JNode() {

	private val wrapper: JadxWrapper = mainWindow.getWrapper()
	private val mainWindow: MainWindow = mainWindow

	private var flatPackages: Boolean = false

	private val customNodes: MutableList<JNode> = ArrayList()

	fun update() {
		removeAllChildren()
		add(JInputs(mainWindow))
		add(JSources(this, wrapper))

		val resources = wrapper.resources
		if (resources.isNotEmpty()) {
			add(getHierarchyResources(resources))
		}
		for (customNode in customNodes) {
			add(customNode)
		}
	}

	private fun getHierarchyResources(resources: List<ResourceFile>): JResource {
		val root = JResource(null, NLS.str("tree.resources_title"), JResType.ROOT)
		for (rf in resources) {
			val rfName = rf.getDeobfName()
			val parts = SPLIT_PATH_PATTERN.split(rfName)
			var curRf = root
			val count = parts.size
			for (i in 0 until count - 1) {
				val name = parts[i]
				var subRF = getSubNodeByName(curRf, name)
				if (subRF == null) {
					subRF = JResource(null, name, JResType.DIR)
					curRf.addSubNode(subRF)
				}
				curRf = subRF
			}
			val leaf = JResource(rf, rf.getDeobfName(), parts[count - 1], JResType.FILE)
			curRf.addSubNode(leaf)
		}
		JResource.mergeMiddleDirs(root)
		root.sortSubNodes()
		root.update()
		return root
	}

	fun searchResourceByName(name: String): JResource? {
		val en = this.breadthFirstEnumeration()
		while (en.hasMoreElements()) {
			val obj = en.nextElement()
			if (obj is JResource) {
				if (obj.getName() == name) {
					return obj
				}
			}
		}
		return null
	}

	fun searchNode(node: JNode): JNode? {
		val en = this.breadthFirstEnumeration()
		while (en.hasMoreElements()) {
			val obj = en.nextElement()
			if (node == obj) {
				return obj as JNode
			}
		}
		return null
	}

	fun followStaticPath(vararg path: String): JNode {
		val list = path.toList()
		return getNodeByClsPath(this, 0, list)
			?: throw JadxRuntimeException("Incorrect static path in tree: $list")
	}

	val isFlatPackages: Boolean get() = flatPackages

	fun setFlatPackages(flatPackages: Boolean) {
		if (this.flatPackages != flatPackages) {
			this.flatPackages = flatPackages
			update()
		}
	}

	fun replaceCustomNode(node: JNode?) {
		if (node == null) {
			return
		}
		val nodeCls = node.javaClass
		customNodes.removeAll { n -> n.javaClass == nodeCls }
		customNodes.add(node)
	}

	fun getCustomNodes(): List<JNode> = customNodes

	override fun getIcon(): Icon = ROOT_ICON

	override fun getJParent(): JClass? = null

	override fun getID(): String = "JRoot"

	override fun makeString(): String {
		val project: JadxProject = wrapper.getProject()
		if (project.getProjectPath() != null) {
			return project.getName()
		}
		val paths = project.filePaths
		val count = paths.size
		if (count == 0) {
			return "File not open"
		}
		if (count == 1) {
			val fileNamePath: Path? = paths[0].fileName
			if (fileNamePath != null) {
				return fileNamePath.toString()
			}
			return paths[0].toString()
		}
		return "$count files"
	}

	override fun getTooltip(): String? {
		val paths = wrapper.getProject().filePaths
		val count = paths.size
		if (count < 2) {
			return null
		}
		// 显示已加载文件列表（完整路径）
		val sb = StringBuilder("<html>")
		for (p in paths) {
			sb.append(UiUtils.escapeHtml(p.toString()))
			sb.append("<br>")
		}
		sb.append("</html>")
		return sb.toString()
	}

	companion object {
		private const val serialVersionUID = 8888495789773527342L

		private val ROOT_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/rootPackageFolder")
		private val SPLIT_PATH_PATTERN: Pattern = Pattern.compile("[/\\\\]+")

		fun getSubNodeByName(rf: JResource, name: String): JResource? {
			for (sub in rf.getSubNodes()) {
				if (sub.getName() == name) {
					return sub
				}
			}
			return null
		}

		private fun getNodeByClsPath(start: JNode, pos: Int, path: List<String>): JNode? {
			if (pos >= path.size) {
				return start
			}
			val clsName = path[pos]
			val en = start.children()
			while (en.hasMoreElements()) {
				val node = en.nextElement() as JNode
				if (node.javaClass.simpleName == clsName) {
					return getNodeByClsPath(node, pos + 1, path)
				}
			}
			return null
		}
	}
}
