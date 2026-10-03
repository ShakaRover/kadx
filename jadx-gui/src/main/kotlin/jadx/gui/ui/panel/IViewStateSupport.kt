package jadx.gui.ui.panel

import jadx.gui.ui.codearea.EditorViewState

/**
 * 「编辑器视图状态」能力接口。
 *
 * **做什么**：标签页在切换/关闭时，需要保存或恢复光标位置、滚动位置等视图状态。
 * 实现该接口的内容面板（如 `CodeContentPanel`）会把这些状态写入/读出 [EditorViewState]。
 *
 * **为什么保留为接口**：由 Java/Kotlin 双方实现，方法签名必须与原 Java 完全一致。
 */
interface IViewStateSupport {

	/** 把当前视图状态写入 [viewState]。 */
	fun saveEditorViewState(viewState: EditorViewState)

	/** 从 [viewState] 恢复视图状态。 */
	fun restoreEditorViewState(viewState: EditorViewState)
}
