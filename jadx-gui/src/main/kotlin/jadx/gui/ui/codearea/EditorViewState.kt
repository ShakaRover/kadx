package jadx.gui.ui.codearea

import jadx.gui.treemodel.JNode
import java.awt.Point

/**
 * 编辑器视图状态：记录某个标签页节点当前的光标位置、滚动位置与标签属性。
 *
 * **做什么**：切换标签页 / 重启后恢复界面时，需要把“之前看到哪里、光标在哪”还原。
 * 除了位置信息，还保存是否激活、是否固定、是否有书签、是否隐藏、是否是预览标签。
 *
 * **为什么用显式 `isXxx()` 函数而不是布尔属性**：Java 调用方按 `isPinned()` 等命名访问，
 * Kotlin 布尔属性会生成 `getPinned()`，签名不匹配。
 */
class EditorViewState {
	private val node: JNode
	private var caretPos: Int
	private var viewPoint: Point
	private var subPath: String

	private var active = false

	private var pinned = false
	private var bookmarked = false
	private var hidden = false
	private var previewTab = false

	constructor(node: JNode) : this(node, "", 0, ZERO)

	constructor(node: JNode, subPath: String, caretPos: Int, viewPoint: Point) {
		this.node = node
		this.subPath = subPath
		this.caretPos = caretPos
		this.viewPoint = viewPoint
	}

	fun getNode(): JNode = node

	fun getCaretPos(): Int = caretPos

	fun setCaretPos(caretPos: Int) {
		this.caretPos = caretPos
	}

	fun getViewPoint(): Point = viewPoint

	fun setViewPoint(viewPoint: Point) {
		this.viewPoint = viewPoint
	}

	fun getSubPath(): String = subPath

	fun setSubPath(subPath: String) {
		this.subPath = subPath
	}

	val isActive: Boolean get() = active

	fun setActive(active: Boolean) {
		this.active = active
	}

	val isPinned: Boolean get() = pinned

	fun setPinned(pinned: Boolean) {
		this.pinned = pinned
	}

	val isBookmarked: Boolean get() = bookmarked

	fun setBookmarked(bookmarked: Boolean) {
		this.bookmarked = bookmarked
	}

	val isHidden: Boolean get() = hidden

	fun setHidden(hidden: Boolean) {
		this.hidden = hidden
	}

	val isPreviewTab: Boolean get() = previewTab

	fun setPreviewTab(previewTab: Boolean) {
		this.previewTab = previewTab
	}

	override fun toString(): String = "EditorViewState{node=" + node +
		", caretPos=" + caretPos +
		", viewPoint=" + viewPoint +
		", subPath='" + subPath + '\'' +
		", active=" + active +
		'}'

	companion object {
		val ZERO: Point = Point(0, 0)
	}
}
