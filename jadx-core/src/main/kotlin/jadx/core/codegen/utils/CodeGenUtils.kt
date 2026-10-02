package jadx.core.codegen.utils

import jadx.api.CommentsLevel
import jadx.api.ICodeWriter
import jadx.api.data.CommentStyle
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.SourceFileAttr
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.attributes.nodes.NotificationAttrNode
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.ICodeNode
import jadx.core.utils.Utils
import java.util.function.BiConsumer
import java.util.regex.Pattern

/**
 * 代码生成辅助工具：把节点上的“错误 / 注释 / 重命名 / 来源文件”等信息输出为代码注释。
 *
 * **Kotlin 转换说明**：原 Java 全部为静态方法，这里放入 `companion object` 并加 `@JvmStatic`，
 * 保证 Java 调用方（以及同模块 Kotlin）继续以 `CodeGenUtils.xxx(...)` 方式调用。
 */
class CodeGenUtils {

	companion object {

		/** 依次输出用户注释与错误信息（原 Java 顺序：先注释后错误）。 */
		@JvmStatic
		fun addErrorsAndComments(code: ICodeWriter, node: NotificationAttrNode) {
			addComments(code, node)
			addErrors(code, node)
		}

		/** 输出节点上收集到的反编译错误（去重并按错误文本排序）。 */
		@JvmStatic
		fun addErrors(code: ICodeWriter, node: NotificationAttrNode) {
			if (!node.checkCommentsLevel(CommentsLevel.ERROR)) {
				return
			}
			val errors = node.getAll(AType.JADX_ERROR)
			if (!errors.isEmpty()) {
				errors.stream().distinct().sorted().forEach { err ->
					addError(code, err.error, err.cause)
				}
			}
		}

		/** 输出单条错误注释，附带异常堆栈（如果有）。 */
		@JvmStatic
		fun addError(code: ICodeWriter, errMsg: String, cause: Throwable?) {
			code.startLine("/*  JADX ERROR: ").add(errMsg)
			if (cause != null) {
				code.incIndent()
				Utils.appendStackTrace(code, cause)
				code.decIndent()
			}
			code.add("*/")
		}

		/** 输出节点上的 JADX 注释与用户代码注释。 */
		@JvmStatic
		fun addComments(code: ICodeWriter, node: NotificationAttrNode) {
			val commentsAttr = node.get(AType.JADX_COMMENTS)
			if (commentsAttr != null) {
				commentsAttr.formatAndFilter(node.getCommentsLevel())
					.forEach { comment -> code.startLine("/* ").addMultiLine(comment).add(" */") }
			}
			addCodeComments(code, node, node)
		}

		/** 只有父节点允许显示用户注释时才输出。 */
		@JvmStatic
		fun addCodeComments(code: ICodeWriter, parent: NotificationAttrNode, node: IAttributeNode?) {
			if (node == null) {
				return
			}
			if (parent.checkCommentsLevel(CommentsLevel.USER_ONLY)) {
				addCodeComments(code, node)
			}
		}

		/** 输出节点上的用户代码注释；指令的注释与指令同一行，其余另起一行。 */
		private fun addCodeComments(code: ICodeWriter, node: IAttributeNode?) {
			if (node == null) {
				return
			}
			val startNewLine = node is ICodeNode // add on same line for instructions
			for (comment in node.getAll(AType.CODE_COMMENTS)) {
				addCodeComment(code, comment, startNewLine)
			}
		}

		private fun addCodeComment(code: ICodeWriter, comment: CodeComment, startNewLine: Boolean) {
			if (startNewLine) {
				code.startLine()
			} else {
				code.add(' ')
			}
			addCommentWithStyle(code, comment.style, comment.comment)
		}

		/** 输出带自定义内容的 JADX 注释（内容由 commentFunc 填充）。 */
		@JvmStatic
		fun addJadxNodeComment(
			code: ICodeWriter,
			node: NotificationAttrNode,
			level: CommentsLevel,
			commentFunc: BiConsumer<ICodeWriter, String>,
		) {
			if (node.checkCommentsLevel(level)) {
				code.startLine()
				addCommentWithStyle(code, CommentStyle.BLOCK_CONDENSED) { commentCode, newLinePrefix ->
					commentCode.add("JADX ").add(level.name).add(": ")
					commentFunc.accept(commentCode, newLinePrefix)
				}
			}
		}

		/** 输出一行固定文本的 JADX 注释。 */
		@JvmStatic
		fun addJadxComment(code: ICodeWriter, level: CommentsLevel, commentStr: String) {
			code.startLine()
			addCommentWithStyle(code, CommentStyle.BLOCK_CONDENSED, "JADX " + level.name + ": " + commentStr)
		}

		private fun addCommentWithStyle(code: ICodeWriter, style: CommentStyle, commentStr: String) {
			appendMultiLineString(code, "", style.start)
			appendMultiLineString(code, style.onNewLine, commentStr)
			appendMultiLineString(code, "", style.end)
		}

		/** 用函数生成注释内容；第二个参数作为换行后的前缀（由 style 决定）。 */
		private fun addCommentWithStyle(
			code: ICodeWriter,
			style: CommentStyle,
			commentFunc: BiConsumer<ICodeWriter, String>,
		) {
			appendMultiLineString(code, "", style.start)
			commentFunc.accept(code, style.onNewLine)
			appendMultiLineString(code, "", style.end)
		}

		/** 匹配任意换行符（\R 等价于 \r\n|\r|\n 等）。 */
		private val NEW_LINE_PATTERN: Pattern = Pattern.compile("\\R")

		private fun appendMultiLineString(code: ICodeWriter, onNewLine: String, str: String) {
			val lines = NEW_LINE_PATTERN.split(str)
			val linesCount = lines.size
			if (linesCount == 0) {
				return
			}
			code.add(lines[0])
			for (i in 1 until linesCount) {
				code.startLine(onNewLine)
				code.add(lines[i])
			}
		}

		/** 类被重命名时输出“renamed from: 原名”注释。 */
		@JvmStatic
		fun addClassRenamedComment(code: ICodeWriter, cls: ClassNode) {
			val classInfo = cls.classInfo
			if (classInfo.hasAlias()) {
				addRenamedComment(code, cls, classInfo.type.getObject())
			}
		}

		/** 通用重命名注释，可选附带重命名原因。 */
		@JvmStatic
		fun addRenamedComment(code: ICodeWriter, node: NotificationAttrNode, origName: String) {
			addJadxNodeComment(code, node, CommentsLevel.INFO) { commentCode, _ ->
				commentCode.add("renamed from: ").add(origName)
				val renameReasonAttr: RenameReasonAttr? = node.get(AType.RENAME_REASON)
				if (renameReasonAttr != null) {
					commentCode.add(", reason: ").add(renameReasonAttr.getDescription())
				}
			}
		}

		/** 输出源码文件名信息（与顶层类名相同则忽略）。 */
		@JvmStatic
		fun addSourceFileInfo(code: ICodeWriter, node: ClassNode) {
			if (!node.checkCommentsLevel(CommentsLevel.INFO)) {
				return
			}
			val sourceFileAttr: SourceFileAttr? = node.get(JadxAttrType.SOURCE_FILE)
			if (sourceFileAttr != null) {
				val fileName = sourceFileAttr.fileName
				val topClsName = node.getTopParentClass().classInfo.shortName
				if (topClsName.contains(fileName)) {
					// ignore similar name
					return
				}
				addJadxComment(code, CommentsLevel.INFO, "compiled from: " + fileName)
			}
		}

		/** 输出输入文件名信息（内部类与外部类相同则忽略）。 */
		@JvmStatic
		fun addInputFileInfo(code: ICodeWriter, cls: ClassNode) {
			val clsData = cls.getClsData()
			if (cls.checkCommentsLevel(CommentsLevel.INFO) && clsData != null) {
				val inputFileName = clsData.getInputFileName()
				if (inputFileName != null) {
					val declCls = cls.getDeclaringClass()
					val declClsData = declCls?.getClsData()
					if (declClsData != null && inputFileName == declClsData.getInputFileName()) {
						// don't add same comment for inner classes
						return
					}
					addJadxComment(code, CommentsLevel.INFO, "loaded from: " + inputFileName)
				}
			}
		}

		/** 从寄存器参数反查其所属的代码变量（无 SSA 变量时返回 null）。 */
		@JvmStatic
		fun getCodeVar(arg: RegisterArg): CodeVar? {
			val svar = arg.sVar
			if (svar != null) {
				return svar.codeVar
			}
			return null
		}
	}
}
