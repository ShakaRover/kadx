package jadx.gui.ui.codearea

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.folding.Fold
import org.fife.ui.rsyntaxtextarea.folding.FoldParser
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager
import org.fife.ui.rsyntaxtextarea.folding.FoldType
import org.slf4j.LoggerFactory
import java.util.TreeSet
import java.util.regex.Pattern

/**
 * Smali 代码折叠解析器：按 `.class` / `.method` / `.end method` 生成折叠区间。
 *
 * **做什么**：每个 `.class` 作为一个外层折叠，其内部的每对 `.method ... .end method`
 * 作为子折叠。这样在查看 Smali 时可以折叠类与方法体。
 *
 * **为什么用 [TreeSet]**：需要在偏移量上做 `floor` / `ceiling` 查询
 * （找最近的 `.end method`、下一个 `.method`），有序集合最合适。
 */
class SmaliFoldParser private constructor() : FoldParser {

	override fun getFolds(textArea: RSyntaxTextArea): List<Fold> {
		val classFolds = ArrayList<Fold>()
		val text = textArea.getText()

		val classStartOffsets = getClassStartOffsets(text)
		val startMethodStartOffsets = getStartMethodStartOffsets(text)
		val endMethodEndOffsets = getEndMethodEndOffsets(text)
		for (i in classStartOffsets.indices) {
			// 当前 .class 的起始偏移
			val startOffset = classStartOffsets[i]

			val classLimit: Int
			if (i < classStartOffsets.size - 1) {
				classLimit = classStartOffsets[i + 1]
			} else {
				classLimit = text.length
			}

			// 取下一个 .class 或文件结束之前最后一个 ".end method"
			val endOffset = endMethodEndOffsets.floor(classLimit)
			if (endOffset != null) {
				val classFold = createFold(textArea, startOffset, endOffset)
				if (classFold != null) {
					classFolds.add(classFold)

					// 从 .class 定义之后开始找 .method
					var startMethodStartOffset: Int? = startMethodStartOffsets.ceiling(startOffset)
					while (startMethodStartOffset != null && startMethodStartOffset < endOffset) {
						val endMethodEndOffset: Int? = endMethodEndOffsets.ceiling(startMethodStartOffset)
						if (endMethodEndOffset != null) {
							addFold(classFold, startMethodStartOffset, endMethodEndOffset)
						}
						// 从上一个 .end method 之后继续找下一个 .method
						startMethodStartOffset = startMethodStartOffsets.ceiling(endMethodEndOffset)
					}
				}
			}
		}
		return classFolds
	}

	private fun getClassStartOffsets(text: String): List<Int> {
		val startOffsets = ArrayList<Int>()
		val matcher = CLASS_LINE_PATTERN.matcher(text)
		while (matcher.find()) {
			startOffsets.add(matcher.start())
		}
		return startOffsets
	}

	private fun getStartMethodStartOffsets(text: String): TreeSet<Int> {
		val startOffsets = TreeSet<Int>()
		val matcher = STARTMETHOD_LINE_PATTERN.matcher(text)
		while (matcher.find()) {
			startOffsets.add(matcher.start())
		}
		return startOffsets
	}

	private fun getEndMethodEndOffsets(text: String): TreeSet<Int> {
		val endOffsets = TreeSet<Int>()
		val matcher = ENDMETHOD_LINE_PATTERN.matcher(text)
		while (matcher.find()) {
			endOffsets.add(matcher.end())
		}
		return endOffsets
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(SmaliFoldParser::class.java)

		private val CLASS_LINE_PATTERN = Pattern.compile("^\\.class\\b", Pattern.MULTILINE)
		private val ENDMETHOD_LINE_PATTERN = Pattern.compile("^\\.end method\\b", Pattern.MULTILINE)
		private val STARTMETHOD_LINE_PATTERN = Pattern.compile("^\\.method\\b", Pattern.MULTILINE)

		/** 把 Smali 折叠解析器注册到 RSyntaxTextArea 的全局折叠解析器表。 */
		fun register() {
			FoldParserManager.get().addFoldParserMapping(AbstractCodeArea.SYNTAX_STYLE_SMALI, SmaliFoldParser())
		}

		private fun createFold(textArea: RSyntaxTextArea, startOffset: Int, endOffset: Int): Fold? {
			try {
				val fold = Fold(FoldType.CODE, textArea, startOffset)
				fold.setEndOffset(endOffset)
				return fold
			} catch (e: Exception) {
				LOG.error("Failed to create code fold", e)
				return null
			}
		}

		private fun addFold(parent: Fold, startOffset: Int, endOffset: Int) {
			try {
				val fold = parent.createChild(FoldType.CODE, startOffset)
				fold.setEndOffset(endOffset)
			} catch (e: Exception) {
				LOG.error("Failed to add code fold", e)
			}
		}
	}
}
