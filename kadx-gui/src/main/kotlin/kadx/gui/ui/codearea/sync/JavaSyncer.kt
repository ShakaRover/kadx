package kadx.gui.ui.codearea.sync

import kadx.gui.ui.codearea.CodeArea
import kadx.gui.ui.codearea.SmaliArea

/**
 * 把 Java 代码区（Java / Simple / Fallback 视图）同步到另一个区域。
 *
 * **做什么**：先尝试按调试行号同步，失败后再按指令偏移同步。
 * 两个子同步器都持有同一个源区域 [area]。
 */
open class JavaSyncer(area: CodeArea) : CodeAreaSyncer {
	private val debugLineSyncer = DebugLineJavaSyncer(area)
	private val insnOffsetSyncer = InsnOffsetJavaSyncer(area)

	override fun syncTo(to: CodeArea): Boolean = debugLineSyncer.syncTo(to) || insnOffsetSyncer.syncTo(to)

	override fun syncTo(to: SmaliArea): Boolean = debugLineSyncer.syncTo(to) || insnOffsetSyncer.syncTo(to)
}
