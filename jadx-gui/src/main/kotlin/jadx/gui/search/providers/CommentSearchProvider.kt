package jadx.gui.search.providers

import jadx.api.ICodeInfo
import jadx.api.JavaClass
import jadx.api.JavaField
import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.api.data.ICodeComment
import jadx.api.data.IJavaNodeRef
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.gui.JadxWrapper
import jadx.gui.jobs.Cancelable
import jadx.gui.search.ISearchProvider
import jadx.gui.search.SearchSettings
import jadx.gui.settings.JadxProject
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.utils.CacheObject
import jadx.gui.utils.JumpPosition
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.Icon

/**
 * 代码注释搜索提供者。
 *
 * **做什么**：遍历项目里保存的所有代码注释（[ICodeComment]），按搜索词过滤，
 * 再把命中的注释包装成可点击的树节点。方法内注释会包装成 [CodeCommentNode]，
 * 以便点击后跳转到方法里的具体偏移。
 *
 * **为什么不用 `BaseSearchProvider`**：注释数据来自项目文件而不是类列表，
 * 遍历与进度统计方式都不同。
 */
class CommentSearchProvider(
	mw: MainWindow,
	private val searchSettings: SearchSettings,
	searchClasses: List<JavaClass>,
) : ISearchProvider {

	private val wrapper: JadxWrapper = mw.getWrapper()
	private val cacheObject: CacheObject = mw.getCacheObject()
	private val project: JadxProject = mw.getProject()
	private val searchClsSet: Set<JavaClass> = HashSet(searchClasses)

	private var progress = 0

	override fun next(cancelable: Cancelable): JNode? {
		while (!cancelable.isCanceled) {
			val comments = project.codeData.getComments()
			if (progress >= comments.size) {
				return null
			}
			val comment = comments[progress++]
			val result = isMatch(searchSettings, comment)
			if (result != null) {
				return result
			}
		}
		return null
	}

	private fun isMatch(searchSettings: SearchSettings, comment: ICodeComment): JNode? {
		val all = searchSettings.getSearchString().isEmpty()
		if (all || searchSettings.isMatch(comment.getComment())) {
			val refNode = getRefNode(comment)
			if (refNode == null) {
				LOG.warn("Failed to get ref node for comment: {}", comment)
				return null
			}
			if (searchClsSet.contains(checkNotNull(refNode.getRootClass()).getCls())) {
				return getCommentNode(comment, refNode)
			}
		}
		return null
	}

	private fun getCommentNode(comment: ICodeComment, refNode: JNode): RefCommentNode {
		val nodeRef = comment.getNodeRef()
		if (nodeRef.getType() == IJavaNodeRef.RefType.METHOD && comment.getCodeRef() != null) {
			return CodeCommentNode(refNode as JMethod, comment)
		}
		return RefCommentNode(refNode, comment.getComment())
	}

	private fun getRefNode(comment: ICodeComment): JNode? {
		val nodeRef = comment.getNodeRef()
		val javaClass = wrapper.searchJavaClassByOrigClassName(nodeRef.getDeclaringClass()) ?: return null
		val nodeCache = cacheObject.nodeCache
		when (nodeRef.getType()) {
			IJavaNodeRef.RefType.CLASS -> return nodeCache.makeFrom(javaClass)

			IJavaNodeRef.RefType.FIELD -> {
				for (field: JavaField in javaClass.getFields()) {
					if (field.getFieldNode().getFieldInfo().shortId == nodeRef.getShortId()) {
						return nodeCache.makeFrom(field)
					}
				}
			}

			IJavaNodeRef.RefType.METHOD -> {
				for (mth: JavaMethod in javaClass.getMethods()) {
					if (mth.getMethodNode().getMethodInfo().shortId == nodeRef.getShortId()) {
						return nodeCache.makeFrom(mth)
					}
				}
			}

			else -> {
				// PKG 类型没有可跳转的界面节点
			}
		}
		return null
	}

	/** 方法内注释节点：延迟计算注释在反编译代码中的跳转位置。 */
	private class CodeCommentNode(node: JMethod, comment: ICodeComment) : RefCommentNode(node, comment.getComment()) {
		private val offset: Int
		private var pos: JumpPosition? = null

		init {
			val codeRef = comment.getCodeRef() ?: throw NullPointerException("Null comment code ref")
			this.offset = codeRef.getIndex()
		}

		override fun getPos(): Int = cachedPos.getPos()

		@get:Synchronized
		private val cachedPos: JumpPosition get() {
			var cached = pos
			if (cached == null) {
				cached = jumpPos
				pos = cached
			}
			return cached
		}

		/** 延迟反编译以定位注释所在位置（仅在需要时触发）。 */
		private val jumpPos: JumpPosition get() {
			val javaMethod = (node as JMethod).javaMethod
			val codeInfo: ICodeInfo = javaMethod.getTopParentClass().getCodeInfo()
			val methodDefPos = javaMethod.getDefPos()
			val jump = codeInfo.getCodeMetadata().searchDown<JumpPosition?>(methodDefPos) { p, ann ->
				if (ann is InsnCodeOffset && ann.getOffset() == offset) {
					JumpPosition(node, p)
				} else {
					null
				}
			}
			if (jump != null) {
				return jump
			}
			return JumpPosition(node)
		}

		companion object {
			private const val serialVersionUID = 6208192811789176886L
		}
	}

	/** 注释节点基类：把展示行为委托给被注释的节点，仅在描述里显示注释文本。 */
	private open class RefCommentNode(
		protected val node: JNode,
		protected val comment: String,
	) : JNode() {

		override fun getRootClass(): JClass? = node.getRootClass()

		override fun getJavaNode(): JavaNode? = node.getJavaNode()

		override fun getJParent(): JClass? = node.getJParent()

		override fun getIcon(): Icon? = node.getIcon()

		override fun getSyntaxName(): String? = SyntaxConstants.SYNTAX_STYLE_NONE // 注释始终是纯文本

		override fun makeString(): String = node.makeString()

		override fun makeLongString(): String = node.makeLongString()

		override fun makeStringHtml(): String = node.makeStringHtml()

		override fun makeLongStringHtml(): String = node.makeLongStringHtml()

		override fun disableHtml(): Boolean = node.disableHtml()

		override fun getPos(): Int = node.getPos()

		override fun getTooltip(): String? = node.getTooltip()

		override fun makeDescString(): String = comment

		override fun hasDescString(): Boolean = true

		companion object {
			private const val serialVersionUID = 3887992236082515752L
		}
	}
	override fun progress(): Int = progress

	override fun total(): Int = project.codeData.getComments().size

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CommentSearchProvider::class.java)
	}
}
