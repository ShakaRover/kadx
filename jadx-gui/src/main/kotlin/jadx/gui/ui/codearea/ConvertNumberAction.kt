package jadx.gui.ui.codearea

import jadx.api.data.CommentStyle
import jadx.api.data.impl.JadxCodeComment
import jadx.gui.treemodel.JClass
import jadx.gui.ui.action.ActionModel
import jadx.gui.ui.dialog.CommentDialog
import jadx.gui.utils.NLS
import org.fife.ui.rsyntaxtextarea.Token
import java.awt.event.ActionEvent
import java.util.regex.Pattern
import javax.swing.event.PopupMenuEvent
import javax.swing.text.BadLocationException

/**
 * “数字格式转换”动作：把光标下的数字在十进制 / 十六进制 / 二进制 / ASCII 字符之间转换。
 *
 * **做什么**：右键菜单打开时解析光标下的数字，动态把菜单标题改成可转换出的结果列表；
 * 点击后把结果写进该行注释。
 *
 * **为什么继承 [CommentAction]**：转换结果是以注释形式写入的，复用注释的定位与写入逻辑。
 */
class ConvertNumberAction(codeArea: CodeArea) : CommentAction(ActionModel.CONVERT_NUMBER, codeArea) {
	private var codeComment: String? = null

	init {
		setEnabled(false)
		setNameAndDesc(DEFAULT_TEXT)
	}

	override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
		if (getCodeArea().getNode() is JClass) {
			// 尝试从光标下的单词解析数字，并动态设置菜单文字
			val word = getWordByPosition(getCodeArea().getCaretPosition())
			val conversions = getConversionsFromWord(word)
			if (conversions.isNotEmpty()) {
				val text = conversions.joinToString(" | ")
				codeComment = text
				setName("$DEFAULT_TEXT: $text")
				setShortDescription(TOOLTIP_TEXT)
				setEnabled(true)
			}
		}
	}

	override fun popupMenuCanceled(e: PopupMenuEvent) {
		// 取消时把菜单恢复为禁用
		setEnabled(false)
		setNameAndDesc(DEFAULT_TEXT)
		codeComment = null
	}

	override fun actionPerformed(e: ActionEvent) {
		if (!enabled) {
			return
		}
		val newText = codeComment ?: return
		val comment = getCommentRef(getCodeArea().getCaretPosition()) ?: return
		val newComment = JadxCodeComment(comment.getNodeRef(), comment.getCodeRef(), newText, CommentStyle.LINE)
		CommentDialog.updateCommentsData(getCodeArea()) { list -> list.add(newComment) }
	}

	/**
	 * 类似 `AbstractCodeArea::getWordByPosition`，但额外支持负数前面的 `-`。
	 */
	fun getWordByPosition(offset: Int): String? {
		val token: Token = getCodeArea().getWordTokenAtOffset(offset) ?: return null
		var str = token.getLexeme()
		try {
			val prev = getCodeArea().getText(token.getOffset() - 1, 1)
			if (prev == "-") {
				str = "-$str"
			}
		} catch (e: BadLocationException) {
			// 忽略
		}
		val len = str.length
		if (len > 2 && str.startsWith("\"") && str.endsWith("\"")) {
			return str.substring(1, len - 1)
		}
		return str
	}

	// TODO: number parsing

	companion object {
		private val DEFAULT_TEXT = NLS.str("popup.convert_number")
		private val TOOLTIP_TEXT = NLS.str("popup.convert_number_tooltip")

		private val NUMBER_FORMAT = Pattern.compile("(-?\\d+L?|0x[0-9A-Fa-f]+)")

		/**
		 * 尝试从输入字符串解析数字，返回该数字的不同进制表示列表。
		 * 例如输入十六进制会转换为十进制与二进制。
		 */
		@JvmStatic
		fun getConversionsFromWord(word: String?): List<String> {
			if (word == null || word.isEmpty() || word == "0" || word == "0L") {
				return emptyList()
			}
			if (!NUMBER_FORMAT.matcher(word).matches()) {
				return emptyList()
			}
			var text = word
			var i32: Int
			var i64: Long
			val radix: Int
			var parsedLong = false
			if (text.startsWith("0x")) {
				text = text.substring(2)
				radix = 16
			} else {
				radix = 10
			}
			// 处理 "12345L" 这样的长整型语法
			if (text.endsWith("L")) {
				text = text.substring(0, text.length - 1)
				i64 = tryParseLong(text, radix)
				i32 = i64.toInt()
				parsedLong = true
				if (i64 == 0L) {
					return emptyList()
				}
			} else {
				i32 = tryParseInt(text, radix)
				if (i32 != 0) {
					i64 = i32.toLong()
				} else {
					i64 = tryParseLong(text, radix)
					parsedLong = true
					if (i64 == 0L) {
						return emptyList()
					}
				}
			}
			val conversions = ArrayList<String>()
			// 十进制输入输出十六进制，反之亦然
			if (radix == 10) {
				if (parsedLong) {
					conversions.add(String.format("0x%x", i64))
				} else {
					conversions.add(String.format("0x%x", i32))
				}
			} else {
				conversions.add(if (parsedLong) i64.toString() else i32.toString())
			}

			// 二进制按 8 位一组补零
			var padBits = (Math.ceil((64 - java.lang.Long.numberOfLeadingZeros(i64)) / 8.0) * 8).toInt()
			if (padBits < 8) {
				padBits = 8
			}
			if (!parsedLong && padBits > 32) {
				padBits = 32
			}
			val binaryString = if (parsedLong) java.lang.Long.toBinaryString(i64) else java.lang.Integer.toBinaryString(i32)
			val fmt = String.format("0b%%%ds", padBits)
			conversions.add(String.format(fmt, binaryString).replace(' ', '0'))

			// 可打印 ASCII 字符
			if (i32 >= ' '.code && i32 <= '~'.code) {
				conversions.add(String.format("'%c'", i32))
			}
			return conversions
		}

		private fun tryParseInt(str: String, radix: Int): Int = try {
			str.toInt(radix)
		} catch (e: NumberFormatException) {
			0
		}

		private fun tryParseLong(str: String, radix: Int): Long = try {
			str.toLong(radix)
		} catch (e: NumberFormatException) {
			0L
		}
	}
}
