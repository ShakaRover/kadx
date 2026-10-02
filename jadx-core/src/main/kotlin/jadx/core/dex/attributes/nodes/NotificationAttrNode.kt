package jadx.core.dex.attributes.nodes

import jadx.api.CommentsLevel
import jadx.api.data.CommentStyle
import jadx.core.codegen.utils.CodeComment
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.ICodeNode
import jadx.core.utils.ErrorsCounter
import jadx.core.utils.Utils

/**
 * 通知型属性节点基类：为类/方法/字段节点提供“错误、警告、注释”的统一入口。
 *
 * **设计意图**：反编译过程中会产生大量诊断信息（错误、警告、信息、调试），
 * 这些信息既可能进入全局错误计数（[ErrorsCounter]），也可能作为代码注释
 * （[JadxCommentsAttr]）输出到反编译结果中。本基类把两者的调用方式统一起来，
 * [jadx.core.dex.nodes.ClassNode]、`MethodNode`、`FieldNode` 都继承它。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 类为 `abstract` 且方法可被子类覆写，这里方法统一加 `open`，
 *   保持 JVM 层面的可覆写语义；
 * - 所有方法都只是转发调用，无状态，因此不涉及属性/getter 冲突。
 */
abstract class NotificationAttrNode :
	LineAttrNode(),
	ICodeNode {

	/** 判断当前节点配置的注释级别是否达到 [required]（用于决定某类注释是否输出） */
	open fun checkCommentsLevel(required: CommentsLevel): Boolean = required.filter(this.root().getArgs().getCommentsLevel())

	/** 记录一个反编译错误（计入全局错误统计，并带上异常堆栈） */
	open fun addError(errStr: String, e: Throwable) {
		ErrorsCounter.error(this, errStr, e)
	}

	/** 记录一个警告：计入全局统计、追加 WARN 注释，并标记代码不一致 */
	open fun addWarn(warn: String) {
		ErrorsCounter.warning(this, warn)
		JadxCommentsAttr.add(this, CommentsLevel.WARN, warn)
		this.add(AFlag.INCONSISTENT_CODE)
	}

	/** 追加一条单行代码注释 */
	open fun addCodeComment(comment: String) {
		addAttr(AType.CODE_COMMENTS, CodeComment(comment, CommentStyle.LINE))
	}

	/** 追加一条指定样式的代码注释 */
	open fun addCodeComment(comment: String, style: CommentStyle) {
		addAttr(AType.CODE_COMMENTS, CodeComment(comment, style))
	}

	/** 追加一条警告注释（不改变代码一致性标记） */
	open fun addWarnComment(warn: String) {
		JadxCommentsAttr.add(this, CommentsLevel.WARN, warn)
	}

	/** 追加一条带异常堆栈的警告注释 */
	open fun addWarnComment(warn: String, exc: Throwable) {
		val commentStr = warn + root().getArgs().getCodeNewLineStr() + Utils.getStackTrace(exc)
		JadxCommentsAttr.add(this, CommentsLevel.WARN, commentStr)
	}

	/** 追加一条信息级注释 */
	open fun addInfoComment(commentStr: String) {
		JadxCommentsAttr.add(this, CommentsLevel.INFO, commentStr)
	}

	/** 追加一条调试级注释 */
	open fun addDebugComment(commentStr: String) {
		JadxCommentsAttr.add(this, CommentsLevel.DEBUG, commentStr)
	}

	/** 取当前节点配置的注释级别 */
	open fun getCommentsLevel(): CommentsLevel = this.root().getArgs().getCommentsLevel()
}
