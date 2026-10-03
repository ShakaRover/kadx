package jadx.gui.search.providers

import jadx.api.ICodeCache
import jadx.api.JavaClass
import jadx.api.metadata.ICodeMetadata
import jadx.api.utils.CodeUtils
import jadx.core.utils.Utils
import jadx.gui.JadxWrapper
import jadx.gui.jobs.Cancelable
import jadx.gui.search.SearchSettings
import jadx.gui.treemodel.CodeNode
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 反编译代码内容搜索提供者。
 *
 * **做什么**：逐个类取反编译源码，在源码里查找搜索词；命中后截取所在行，
 * 并尽量解析出该位置所属的方法 / 字段等外层节点，封装成 [CodeNode] 返回。
 *
 * **为什么要 `includedClasses`**：批量反编译时每个批次只包含部分类，其余类需要
 * 主动调用 [JavaClass.decompile] 触发反编译以保证后续能命中。
 *
 * **为什么不是 `data class`**：它带游标与当前类代码缓存，属于有状态迭代器。
 */
class CodeSearchProvider(
	mw: MainWindow,
	searchSettings: SearchSettings,
	classes: List<JavaClass>,
	private val includedClasses: Set<JavaClass>?,
) : BaseSearchProvider(mw, searchSettings, classes) {

	private val codeCache: ICodeCache = mw.getWrapper().args.codeCache
	private val wrapper: JadxWrapper = mw.getWrapper()

	private var code: String? = null
	private var clsNum = 0
	private var pos = 0

	override fun next(cancelable: Cancelable): JNode? {
		val inclCls = includedClasses
		while (true) {
			if (cancelable.isCanceled || clsNum >= classes.size) {
				return null
			}

			val cls = classes[clsNum]
			if (inclCls == null || inclCls.contains(cls)) {
				var clsCode = code
				if (clsCode == null && !cls.isInner() && !cls.isNoCode()) {
					clsCode = getClassCode(cls, codeCache)
				}
				if (clsCode != null) {
					val newResult = searchNext(cls, clsCode)
					if (newResult != null) {
						code = clsCode
						return newResult
					}
				}
			} else {
				// 未包含在本批次的类，强制反编译以便后续使用
				cls.decompile()
			}
			clsNum++
			pos = 0
			code = null
		}
	}

	private fun searchNext(javaClass: JavaClass, clsCode: String): JNode? {
		val newPos = searchMth.find(clsCode, searchStr, pos)
		if (newPos == -1) {
			return null
		}
		val lineStart = 1 + CodeUtils.getNewLinePosBefore(clsCode, newPos)
		val lineEnd = CodeUtils.getNewLinePosAfter(clsCode, newPos)
		val end = if (lineEnd == -1) clsCode.length else lineEnd
		val line = clsCode.substring(lineStart, end)
		this.pos = end
		val rootCls = convert(javaClass)
		val enclosingNode = Utils.getOrElse(getEnclosingNode(javaClass, end), rootCls)
		return CodeNode(rootCls, enclosingNode, line.trim(), newPos)
	}

	private fun getEnclosingNode(javaCls: JavaClass, pos: Int): JNode? {
		try {
			val metadata: ICodeMetadata = javaCls.getCodeInfo().getCodeMetadata()
			val nodeRef = metadata.getNodeAt(pos) ?: return null
			val encNode = wrapper.getJavaNodeByRef(nodeRef)
			if (encNode != null) {
				return convert(encNode)
			}
		} catch (e: Exception) {
			LOG.debug("Failed to resolve enclosing node", e)
		}
		return null
	}

	private fun getClassCode(javaClass: JavaClass, codeCache: ICodeCache): String {
		try {
			// 先快速检查缓存里是否已有代码
			val code = codeCache.getCode(javaClass.getRawName())
			if (code != null) {
				return code
			}
			// 触发反编译
			return javaClass.getCode()
		} catch (e: Exception) {
			LOG.warn("Failed to get class code: {}", javaClass, e)
			return ""
		}
	}

	override fun progress(): Int = clsNum

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CodeSearchProvider::class.java)
	}
}
