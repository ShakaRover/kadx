package kadx.gui.ui.tab.dnd

/**
 * 标签页拖拽时“幽灵”预览的渲染方式。
 */
enum class TabDndGhostType {
	/**
	 * 从标签组件生成位图，随光标拖动。
	 * 可能对性能有一定影响。
	 */
	IMAGE,

	/**
	 * 随光标拖动一个与标签同尺寸的彩色矩形。
	 */
	OUTLINE,

	/**
	 * 只渲染插入位置标记。
	 */
	TARGET_MARK,
}
