package jadx.gui.ui.codearea.sync

import jadx.gui.ui.codearea.AbstractCodeArea
import java.awt.Color
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter
import javax.swing.text.Highlighter.HighlightPainter

/**
 * 代码区域高亮 / 滚动工具。
 *
 * **做什么**：用指定颜色在代码区高亮某一行或某段字符，并可选滚动到该位置。
 * 高亮是“临时”的：1 秒后由 [Timer] 自动移除，避免高亮残留。
 *
 * **为什么用 [Timer]**：保持原 Swing 线程模型（EDT 上定时回调），不引入协程。
 */
class CodeSyncHighlighter(private val color: Color?) {

	/** 高亮 [lineIndex] 行并滚动到该行。 */
	fun highlightAndScrollToLine(area: AbstractCodeArea, lineIndex: Int) {
		highlightLine(area, lineIndex)
		area.scrollToPos(area.getLineStartOffset(lineIndex))
	}

	/** 只高亮 [lineIndex] 行，不滚动。 */
	fun highlightLine(area: AbstractCodeArea, lineIndex: Int) {
		val startOffset = area.getLineStartOffset(lineIndex)
		val endOffset = area.getLineEndOffset(lineIndex)
		highlightRange(area, startOffset, endOffset)
	}

	/** 高亮 [startOffset, endOffset) 区间，1 秒后自动清除。 */
	fun highlightRange(area: AbstractCodeArea, startOffset: Int, endOffset: Int) {
		val hl = area.getHighlighter()
		val painter: HighlightPainter = DefaultHighlighter.DefaultHighlightPainter(this.color)
		val tag = hl.addHighlight(startOffset, endOffset, painter)
		Timer(1000) { hl.removeHighlight(tag) }.start()
	}

	companion object {
		/** 使用主题的悬停色创建默认高亮器。 */
		@JvmStatic
		fun defaultHighlighter(): CodeSyncHighlighter = CodeSyncHighlighter(UIManager.getColor("TabbedPane.hoverColor"))
	}
}
