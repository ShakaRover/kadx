package kadx.gui.ui.codearea

import kadx.api.ICodeInfo
import kadx.api.JavaMethod
import kadx.api.JavaNode
import kadx.api.data.ICodeComment
import kadx.api.data.impl.KadxCodeComment
import kadx.api.data.impl.KadxCodeData
import kadx.api.data.impl.KadxCodeRef
import kadx.api.data.impl.KadxNodeRef
import kadx.api.metadata.ICodeAnnotation.AnnType
import kadx.api.metadata.ICodeNodeRef
import kadx.api.metadata.annotations.InsnCodeOffset
import kadx.api.metadata.annotations.NodeDeclareRef
import kadx.gui.treemodel.JClass
import kadx.gui.ui.action.ActionModel
import kadx.gui.ui.action.CodeAreaAction
import kadx.gui.ui.action.KadxGuiAction
import kadx.gui.ui.dialog.CommentDialog
import kadx.gui.utils.DefaultPopupMenuListener
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenTypes
import org.slf4j.LoggerFactory
import java.awt.event.ActionEvent
import javax.swing.event.PopupMenuEvent

/**
 * “添加/更新代码注释”动作。
 *
 * **做什么**：在代码区右键菜单中，根据光标所在行判断能否插入注释：
 * 可以是“某条指令的注释”“某个定义上方的注释”或“某节点定义处的注释”。
 * 若该位置已有注释则转为更新。
 *
 * **为什么实现 [DefaultPopupMenuListener]**：菜单打开/取消时需要动态启用/禁用本动作。
 */
open class CommentAction :
	CodeAreaAction,
	DefaultPopupMenuListener {
	protected val enabled: Boolean
	private var actionComment: ICodeComment? = null
	private var updateComment = false

	constructor(codeArea: CodeArea) : super(ActionModel.CODE_COMMENT, codeArea) {
		enabled = codeArea.getNode() is JClass
	}

	constructor(actionModel: ActionModel, codeArea: CodeArea) : super(actionModel, codeArea) {
		enabled = codeArea.getNode() is JClass
	}

	override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
		if (enabled && updateCommentAction(UiUtils.getOffsetAtMousePosition(getCodeArea()))) {
			setNameAndDesc(if (updateComment) NLS.str("popup.update_comment") else NLS.str("popup.add_comment"))
			setEnabled(true)
		} else {
			setEnabled(false)
		}
	}

	override fun popupMenuCanceled(e: PopupMenuEvent) {
		actionComment = null
		setEnabled(false)
	}

	private fun updateCommentAction(pos: Int): Boolean {
		val codeComment = getCommentRef(pos)
		if (codeComment == null) {
			actionComment = null
			return false
		}
		val exitsComment = searchForExistComment(codeComment)
		if (exitsComment != null) {
			actionComment = exitsComment
			updateComment = true
		} else {
			actionComment = codeComment
			updateComment = false
		}
		return true
	}

	override fun actionPerformed(e: ActionEvent) {
		if (!enabled) {
			return
		}
		if (KadxGuiAction.isSource(e)) {
			updateCommentAction(getCodeArea().getCaretPosition())
		}
		val comment = actionComment
		if (comment == null) {
			UiUtils.showMessageBox(getCodeArea().mainWindow, NLS.str("msg.cant_add_comment"))
			return
		}
		CommentDialog.show(getCodeArea(), comment, updateComment)
	}

	protected fun searchForExistComment(blankComment: ICodeComment): ICodeComment? {
		try {
			val project = getCodeArea().project
			val codeData: KadxCodeData? = project.codeData
			if (codeData == null || codeData.getComments().isEmpty()) {
				return null
			}
			for (comment in codeData.getComments()) {
				if (comment.getNodeRef() == blankComment.getNodeRef() &&
					comment.getCodeRef() == blankComment.getCodeRef()
				) {
					return comment
				}
			}
		} catch (e: Exception) {
			LOG.error("Error searching for exists comment", e)
		}
		return null
	}

	/**
	 * 检查当前位置能否插入注释。
	 *
	 * @return 空白代码注释对象（注释文本为空）
	 */
	protected fun getCommentRef(pos: Int): ICodeComment? {
		if (pos == -1) {
			return null
		}
		try {
			val wrapper = getCodeArea().kadxWrapper
			val codeInfo: ICodeInfo = getCodeArea().getCodeInfo()
			val metadata = codeInfo.codeMetadata
			val lineStartPos = getCodeArea().getLineStartFor(pos)

			// 通过指令偏移添加方法行注释
			val offsetAnn = metadata.searchUp(pos, lineStartPos, AnnType.OFFSET)
			if (offsetAnn is InsnCodeOffset) {
				val node: JavaNode? = wrapper.getJavaNodeByRef(metadata.getNodeAt(pos))
				if (node is JavaMethod) {
					val rawOffset = offsetAnn.getOffset()
					val nodeRef = KadxNodeRef.forMth(node)
					return KadxCodeComment(nodeRef, KadxCodeRef.forInsn(rawOffset), "")
				}
			}

			// 检查本行的定义
			val nodeDef: ICodeNodeRef? = metadata.searchUp(pos) { off, ann ->
				if (lineStartPos <= off && ann.annType == AnnType.DECLARATION) {
					val defRef = (ann as NodeDeclareRef).getNode()
					if (defRef.annType != AnnType.VAR) {
						return@searchUp defRef
					}
				}
				null
			}
			if (nodeDef != null) {
				val nodeRef = KadxNodeRef.forJavaNode(wrapper.getJavaNodeByRef(nodeDef)) ?: return null
				return KadxCodeComment(nodeRef, "")
			}

			// 检查是否位于节点定义上方的注释行
			if (isCommentLine(pos)) {
				val nodeRef: ICodeNodeRef? = metadata.searchDown(pos) { off, ann ->
					if (off > pos && ann.annType == AnnType.DECLARATION) {
						return@searchDown (ann as NodeDeclareRef).getNode()
					}
					null
				}
				if (nodeRef != null) {
					val defNode = wrapper.getJavaNodeByRef(nodeRef)
					val ref = KadxNodeRef.forJavaNode(defNode) ?: return null
					return KadxCodeComment(ref, "")
				}
			}
		} catch (e: Exception) {
			LOG.error("Failed to add comment at: {}", pos, e)
		}
		return null
	}

	/** 判断 [pos] 所在行是否全部由注释 token 组成。 */
	protected fun isCommentLine(pos: Int): Boolean {
		try {
			val line = getCodeArea().getLineOfOffset(pos)
			val lineTokens = getCodeArea().getTokenListForLine(line)
			var commentFound = false
			var t: Token? = lineTokens
			while (t != null) {
				if (t.isComment()) {
					commentFound = true
				} else {
					when (t.getType()) {
						TokenTypes.WHITESPACE, TokenTypes.NULL -> {
							// 允许的 token
						}

						else -> return false
					}
				}
				t = t.getNextToken()
			}
			return commentFound
		} catch (e: Exception) {
			LOG.warn("Failed to check for comment line", e)
			return false
		}
	}

	companion object {
		private const val serialVersionUID = 4753838562204629112L

		private val LOG = LoggerFactory.getLogger(CommentAction::class.java)
	}
}
