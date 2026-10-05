package kadx.gui.ui.codearea

import kadx.gui.treemodel.JNode
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.tab.TabbedPane
import java.awt.Component

/**
 * “基于文本的代码内容面板”的抽象基类。
 *
 * **做什么**：为 Java 代码面板、Smali 面板、资源文本面板提供统一的父类，
 * 暴露一个可选的代码区 [getCodeArea]，并复用 [ContentPanel] 的定位滚动能力。
 *
 * **为什么可空**：像 [BinaryContentPanel] 这类面板没有代码区，会返回 `null`。
 */
abstract class AbstractCodeContentPanel protected constructor(panel: TabbedPane, jnode: JNode) : ContentPanel(panel, jnode) {

	/** 面板内的代码区；纯二进制面板返回 `null`。 */
	abstract fun getCodeArea(): AbstractCodeArea?

	/** 面板真正展示的子组件（代码区或十六进制视图等）。 */
	abstract fun getChildrenComponent(): Component

	override fun scrollToPos(pos: Int) {
		val codeArea = getCodeArea()
		if (codeArea != null) {
			codeArea.requestFocus()
			codeArea.scrollToPos(pos)
		}
	}

	companion object {
		private const val serialVersionUID = 4685846894279064422L
	}
}
