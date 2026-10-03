package jadx.gui.ui.action

import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.dialog.SearchDialog
import java.awt.event.ActionEvent

/**
 * 在“当前标签页”中搜索注释的动作。
 *
 * **做什么**：把菜单/快捷键事件转发给 [SearchDialog]，并预设搜索类型为注释。
 */
class CommentSearchAction(codeArea: CodeArea) : CodeAreaAction(ActionModel.CODE_COMMENT_SEARCH, codeArea) {

	override fun actionPerformed(e: ActionEvent) {
		startSearch()
	}

	private fun startSearch() {
		SearchDialog.searchInActiveTab(checkNotNull(codeArea).mainWindow, SearchDialog.SearchPreset.COMMENT)
	}

	companion object {
		private const val serialVersionUID = -3646341661734961590L
	}
}
