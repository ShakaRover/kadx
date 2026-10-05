package kadx.gui.ui.codearea.sync

import kadx.gui.ui.codearea.CodeArea
import kadx.gui.ui.codearea.SmaliArea
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 把 Smali 代码区同步到另一个区域。
 *
 * **做什么**：
 * - 同步到 Java 代码区时，先按调试行号、再按指令偏移匹配；
 * - 同步到另一个 Smali 区域时，仅在两者视图模式一致时滚动到同一行，
 *   并始终返回 true 以阻止回退（Fallback）同步。
 */
open class SmaliSyncer(private val from: SmaliArea) : CodeAreaSyncer {
	private val insnOffsetSyncer = InsnOffsetSmaliSyncer(from)
	private val debugLineSyncer = DebugLineSmaliSyncer(from)

	override fun syncTo(to: CodeArea): Boolean = debugLineSyncer.syncTo(to) || insnOffsetSyncer.syncTo(to)

	override fun syncTo(to: SmaliArea): Boolean {
		if (from.isShowingDalvikBytecode == to.isShowingDalvikBytecode) {
			// smali -> smali：只有内容模式相同时才滚动到当前行
			to.scrollToPos(from.getLineStartOffsetOfCurrentLine())
		}
		return true // 阻止回退同步
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SmaliSyncer::class.java)
	}
}
