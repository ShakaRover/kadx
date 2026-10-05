package kadx.gui.utils.res

import kadx.api.ICodeInfo
import kadx.api.ResourceFileContent
import kadx.api.ResourceType
import kadx.core.xmlgen.ResContainer
import kadx.gui.treemodel.JResource
import kadx.gui.treemodel.JSubResource

/**
 * 资源表（`resources.arsc`）到资源树节点的构建工具。
 *
 * **做什么**：递归遍历 [ResContainer] 的子项，按名称中的 `/` 还原目录层级，
 * 生成 [JResource] 树（目录节点 + 文件节点）。
 */
class ResTableHelper private constructor(private val resTableRes: JResource) {

	private val roots: MutableList<JResource> = ArrayList()
	private val dirs: MutableMap<String, JResource> = HashMap()

	private fun process(resTable: ResContainer) {
		for (subFile in resTable.subFiles) {
			loadSubNodes(subFile)
		}
	}

	private fun loadSubNodes(rc: ResContainer) {
		val resName = rc.name
		val split = resName.lastIndexOf('/')
		val dir: String?
		val name: String
		if (split == -1) {
			dir = null
			name = resName
		} else {
			dir = resName.substring(0, split)
			name = resName.substring(split + 1)
		}
		val code: ICodeInfo = rc.text
		val fileContent = ResourceFileContent(name, ResourceType.XML, code)
		val resFile = JSubResource(resTableRes, fileContent, resName, name, JResource.JResType.FILE)
		addResFile(dir, resFile)

		for (subFile in rc.subFiles) {
			loadSubNodes(subFile)
		}
	}

	private fun addResFile(dir: String?, resFile: JResource) {
		if (dir == null) {
			roots.add(resFile)
			return
		}
		val dirRes = dirs[dir]
		if (dirRes != null) {
			dirRes.addSubNode(resFile)
			return
		}
		var parentDir: JResource? = null
		var splitPos = -1
		while (true) {
			val prevStart = splitPos + 1
			splitPos = dir.indexOf('/', prevStart)
			val last = splitPos == -1
			val path = if (last) dir else dir.substring(0, splitPos)
			var curDir = dirs[path]
			if (curDir == null) {
				val dirName = if (last) dir.substring(prevStart) else dir.substring(prevStart, splitPos)
				curDir = JSubResource(resTableRes, null, path, dirName, JResource.JResType.DIR)
				dirs[path] = curDir
				if (parentDir == null) {
					roots.add(curDir)
				} else {
					parentDir.addSubNode(curDir)
				}
			}
			if (last) {
				curDir.addSubNode(resFile)
				return
			}
			parentDir = curDir
		}
	}

	companion object {
		/**
		 * 为资源表容器构建 UI 资源树。
		 *
		 * @return 根节点列表
		 */
		fun buildTree(resTableRes: JResource, resTable: ResContainer): MutableList<JResource> {
			val resTableHelper = ResTableHelper(resTableRes)
			resTableHelper.process(resTable)
			JResource.mergeMiddleDirs(resTableHelper.roots)
			return resTableHelper.roots
		}
	}
}
