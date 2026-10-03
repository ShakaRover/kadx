package jadx.gui.utils

import jadx.api.ICodeInfo
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.gui.treemodel.JClass
import jadx.gui.ui.codearea.AbstractCodeArea
import org.fife.ui.rsyntaxtextarea.Token
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.text.BadLocationException

/**
 * 代码刷新后恢复光标位置。
 *
 * **做什么**：类被重命名/加注释等刷新后，文档内容变化无法直接映射光标。
 * 这里在刷新前用「行号 + 行内偏移 + token 序号/类型 + 字节码偏移」保存锚点，
 * 刷新后再据此重新计算光标位置。
 *
 * **Swing 说明**：[restore] 期望在 UI 线程调用（与原 Java 一致）。
 */
class CaretPositionFix(private val codeArea: AbstractCodeArea) {

	private var linesCount = 0
	private var line = 0
	private var pos = 0
	private var lineOffset = 0
	private var tokenInfo: TokenInfo? = null

	private var javaNodePos = -1
	private var codeRawOffset = -1

	/** 以光标下的 token 作为锚点保存当前位置。 */
	fun save() {
		try {
			linesCount = codeArea.getLineCount()
			pos = codeArea.getCaretPosition()
			line = codeArea.getLineOfOffset(pos)
			lineOffset = pos - codeArea.getLineStartOffset(line)

			tokenInfo = getTokenInfoByOffset(codeArea.getTokenListForLine(line), pos)

			val codeInfo: ICodeInfo = codeArea.getCodeInfo()
			if (codeInfo.hasMetadata()) {
				val metadata = codeInfo.getCodeMetadata()
				val ann: ICodeAnnotation? = metadata.getAt(pos)
				if (ann is InsnCodeOffset) {
					codeRawOffset = ann.getOffset()
					val javaNode = metadata.getNodeAt(pos)
					if (javaNode != null) {
						javaNodePos = javaNode.getDefPosition()
					}
				}
			}
			LOG.debug(
				"Saved position data: line={}, lineOffset={}, token={}, codeRawOffset={}, javaNodeLine={}",
				line,
				lineOffset,
				tokenInfo,
				codeRawOffset,
				javaNodePos,
			)
		} catch (e: Exception) {
			LOG.error("Failed to save caret position before refresh", e)
			line = -1
		}
	}

	/** 在刷新后的代码中恢复光标位置。 */
	fun restore() {
		if (line == -1) {
			return
		}
		try {
			val newPos = getNewPos()
			val newLine = codeArea.getLineOfOffset(newPos)
			val token = codeArea.getTokenListForLine(newLine)
			var tokenPos = getOffsetFromTokenInfo(tokenInfo, token)
			if (tokenPos == -1) {
				val lineStartOffset = codeArea.getLineStartOffset(newLine)
				val lineEndOffset = codeArea.getLineEndOffset(newLine) - 1
				val lineLength = lineEndOffset - lineStartOffset
				// 无法按 token 恢复时，退化为按行内偏移恢复
				if (lineOffset < lineLength) {
					tokenPos = lineStartOffset + lineOffset
				} else {
					// 行被截断 -> 光标放到行尾
					tokenPos = lineEndOffset
				}
			}
			codeArea.setCaretPosition(tokenPos)
			LOG.debug("Restored caret position: {}", tokenPos)
		} catch (e: Exception) {
			LOG.warn("Failed to restore caret position", e)
		}
	}

	@Throws(BadLocationException::class)
	private fun getNewPos(): Int {
		val newLinesCount = codeArea.getLineCount()
		if (linesCount == newLinesCount) {
			return pos
		}
		// 行数发生变化：尝试用字节码偏移重新定位
		val codeInfo = codeArea.getCodeInfo()
		if (javaNodePos != -1 && codeInfo.hasMetadata()) {
			val cls: JClass? = codeArea.getJClass()
			if (cls != null) {
				val codeMetadata = codeInfo.getCodeMetadata()
				for ((annPos, ann) in codeMetadata.getAsMap()) {
					if (annPos >= javaNodePos) {
						if (ann is InsnCodeOffset && ann.getOffset() == codeRawOffset) {
							return annPos
						}
					}
				}
			}
		}
		// 兜底：假设光标之前被增删了若干行
		val newLine = line - (linesCount - newLinesCount)
		return codeArea.getLineStartOffset(newLine)
	}

	/** 记录光标所在 token 在行内的序号与类型，作为刷新后的定位锚点。 */
	private fun getTokenInfoByOffset(token: Token?, offset: Int): TokenInfo? {
		var t = token ?: return null
		var index = 1
		while (t.getEndOffset() < offset) {
			t = t.getNextToken() ?: return null
			index++
		}
		return TokenInfo(index, t.getType())
	}

	/** 按 token 序号/类型反查刷新后代码中的偏移；找不到返回 -1。 */
	private fun getOffsetFromTokenInfo(tokenInfo: TokenInfo?, token: Token?): Int {
		if (tokenInfo == null || token == null) {
			return -1
		}
		val index = tokenInfo.getIndex()
		if (index == -1) {
			return -1
		}
		var t: Token = checkNotNull(token)
		for (i in 0 until index) {
			t = t.getNextToken() ?: return -1
		}
		if (t.getType() != tokenInfo.getType()) {
			return -1
		}
		return t.getOffset()
	}

	/** token 锚点信息（行内序号 + token 类型）。 */
	private class TokenInfo(private val index: Int, private val type: Int) {
		fun getIndex(): Int = index

		fun getType(): Int = type

		override fun toString(): String = "Token{index=$index, type=$type}"
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CaretPositionFix::class.java)
	}
}
