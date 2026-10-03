package jadx.gui.ui.codearea

import jadx.api.JavaNode
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.utils.JNodeCache
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rtextarea.SmartHighlightPainter
import org.slf4j.LoggerFactory
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.text.Caret

/**
 * 鼠标悬停高亮：把光标停靠的 token 高亮，并在 tooltip 中显示其完整签名。
 *
 * **做什么**：移动鼠标时解析当前位置的 token，通过 [CodeLinkGenerator] 找到它引用的
 * Java 节点；找到就高亮该 token 并设置悬浮提示，否则移除高亮。
 *
 * **为什么用 [MouseMotionAdapter]**：只关心 `mouseMoved` 一个回调，
 * 用适配器可以避免实现接口里的其他空方法。
 */
internal class MouseHoverHighlighter(
	private val codeArea: CodeArea,
	private val codeLinkGenerator: CodeLinkGenerator,
) : MouseMotionAdapter() {
	private val highlighter = SmartHighlightPainter()
	private var tag: Any? = null
	private var highlightedTokenOffset = -1

	init {
		loadSettings()
	}

	/** 主题切换后重新读取“标记出现处”的颜色。 */
	fun loadSettings() {
		highlighter.setPaint(codeArea.getMarkOccurrencesColor())
	}

	override fun mouseMoved(e: MouseEvent) {
		if (!addHighlight(e)) {
			removeHighlight()
		}
	}

	private fun addHighlight(e: MouseEvent): Boolean {
		if (e.getModifiersEx() != 0) {
			return false
		}
		val caret: Caret = codeArea.getCaret()
		if (caret.getDot() != caret.getMark()) {
			// 正在选择文本，高亮会干扰选区
			return false
		}
		try {
			val token: Token = codeArea.viewToToken(e.getPoint()) ?: return false
			val tokenOffset = token.getOffset()
			if (tokenOffset == highlightedTokenOffset) {
				// 已经是同一个 token，无需重绘
				return true
			}
			val nodeAtOffset = codeLinkGenerator.getNodeAtOffset(tokenOffset) ?: return false
			removeHighlight()
			tag = codeArea.getHighlighter().addHighlight(tokenOffset, token.getEndOffset(), this.highlighter)
			highlightedTokenOffset = tokenOffset
			updateToolTip(nodeAtOffset)
			return true
		} catch (exc: Exception) {
			LOG.error("Mouse hover highlight error", exc)
			return false
		}
	}

	private fun removeHighlight() {
		val currentTag = tag
		if (currentTag != null) {
			codeArea.getHighlighter().removeHighlight(currentTag)
			tag = null
			highlightedTokenOffset = -1
			updateToolTip(null)
		}
	}

	private fun updateToolTip(node: JavaNode?) {
		val mainWindow: MainWindow = codeArea.mainWindow
		if (node == null || mainWindow.getSettings().isDisableTooltipOnHover) {
			codeArea.setToolTipText(null)
			return
		}
		val nodeCache: JNodeCache = mainWindow.getCacheObject().nodeCache
		val jNode: JNode? = nodeCache.makeFrom(node)
		codeArea.setToolTipText(jNode?.getTooltip())
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MouseHoverHighlighter::class.java)
	}
}
