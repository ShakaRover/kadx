package kadx.gui.ui.codearea

import kadx.api.JavaNode
import kadx.gui.treemodel.JNode
import kadx.gui.utils.JumpPosition
import org.fife.ui.rsyntaxtextarea.LinkGenerator
import org.fife.ui.rsyntaxtextarea.LinkGeneratorResult
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.slf4j.LoggerFactory
import javax.swing.event.HyperlinkEvent

/**
 * 为代码区提供 Ctrl+点击 跳转链接的生成器。
 *
 * **做什么**：根据鼠标位置的 token 找到它引用的 [JavaNode]，
 * 如果该节点不是“当前节点自身”，就把它包装成 [LinkGeneratorResult]，
 * 让编辑器把它渲染成可点击的超链接。
 *
 * **为什么要做自身判断**：跳到自己没有意义，会形成无意义的原地跳转。
 */
class CodeLinkGenerator(private val codeArea: CodeArea) : LinkGenerator {
	private val jNode: JNode? = codeArea.getNode()

	/** 返回指定偏移处引用到的 Java 节点，失败或没有元数据时返回 `null`。 */
	fun getNodeAtOffset(offset: Int): JavaNode? {
		try {
			if (!codeArea.getCodeInfo().hasMetadata()) {
				return null
			}
			val sourceOffset = codeArea.adjustOffsetForWordToken(offset)
			if (sourceOffset == -1) {
				return null
			}
			return codeArea.getJavaNodeAtOffset(offset)
		} catch (e: Exception) {
			LOG.error("getNodeAtOffset error", e)
			return null
		}
	}

	override fun isLinkAtOffset(textArea: RSyntaxTextArea, offset: Int): LinkGeneratorResult? {
		try {
			if (!codeArea.getCodeInfo().hasMetadata()) {
				return null
			}
			val sourceOffset = codeArea.adjustOffsetForWordToken(offset)
			if (sourceOffset == -1) {
				return null
			}
			val defPos = getJumpBySourceOffset(sourceOffset) ?: return null
			return object : LinkGeneratorResult {
				override fun execute(): HyperlinkEvent = HyperlinkEvent(
					defPos,
					HyperlinkEvent.EventType.ACTIVATED,
					null,
					defPos.getNode().makeLongString(),
				)

				override fun getSourceOffset(): Int = sourceOffset
			}
		} catch (e: Exception) {
			LOG.error("isLinkAtOffset error", e)
			return null
		}
	}

	private fun getJumpBySourceOffset(sourceOffset: Int): JumpPosition? {
		val defPos = codeArea.getDefPosForNodeAtOffset(sourceOffset) ?: return null
		if (defPos.getNode().getRootClass() == jNode && defPos.getPos() == sourceOffset) {
			// 忽略跳到自身
			return null
		}
		return defPos
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(CodeLinkGenerator::class.java)
	}
}
