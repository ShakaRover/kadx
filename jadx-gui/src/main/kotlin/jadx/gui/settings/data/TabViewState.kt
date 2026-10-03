package jadx.gui.settings.data

/**
 * 单个标签页的持久化状态。
 *
 * **做什么**：保存项目时记录每个打开标签的类型、路径、光标位置、滚动视图，以及
 * 是否激活/固定/书签/隐藏/预览等标记；加载项目时据此恢复标签页。
 *
 * **为什么保留显式 getter/setter 与原始字段名**：本类会被 Gson 写入项目文件，
 * 字段名（`type`/`tabPath`/`subPath`/`caret`/`view`/`active`/...）即持久化 JSON 键，
 * 不能改动；同时 Java 调用方按 `isActive()` 等命名访问，需保留方法名。
 */
class TabViewState {
	private var type: String? = null
	private var tabPath: String? = null
	private var subPath: String? = null
	private var caret: Int = 0
	private var view: ViewPoint? = null
	private var active: Boolean = false
	private var pinned: Boolean = false
	private var bookmarked: Boolean = false
	private var hidden: Boolean = false
	private var previewTab: Boolean = false

	fun getType(): String? = type

	fun setType(type: String?) {
		this.type = type
	}

	fun getTabPath(): String? = tabPath

	fun setTabPath(tabPath: String?) {
		this.tabPath = tabPath
	}

	fun getSubPath(): String? = subPath

	fun setSubPath(subPath: String?) {
		this.subPath = subPath
	}

	fun getCaret(): Int = caret

	fun setCaret(caret: Int) {
		this.caret = caret
	}

	fun getView(): ViewPoint? = view

	fun setView(view: ViewPoint?) {
		this.view = view
	}

	fun isActive(): Boolean = active

	fun setActive(active: Boolean) {
		this.active = active
	}

	fun isPinned(): Boolean = pinned

	fun setPinned(pinned: Boolean) {
		this.pinned = pinned
	}

	fun isBookmarked(): Boolean = bookmarked

	fun setBookmarked(bookmarked: Boolean) {
		this.bookmarked = bookmarked
	}

	fun isHidden(): Boolean = hidden

	fun setHidden(hidden: Boolean) {
		this.hidden = hidden
	}

	fun isPreviewTab(): Boolean = previewTab

	fun setPreviewTab(previewTab: Boolean) {
		this.previewTab = previewTab
	}

	override fun toString(): String = "TabViewState{type=$type, tabPath=$tabPath, subPath=$subPath}"
}
