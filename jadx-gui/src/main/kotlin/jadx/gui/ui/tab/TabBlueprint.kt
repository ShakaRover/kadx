package jadx.gui.ui.tab

import jadx.gui.treemodel.JNode

/**
 * 标签页蓝图：保存一个标签页的持久状态（是否创建、置顶、书签、隐藏、预览）。
 *
 * **做什么**：`TabsController` 用 [JNode] 作为键保存蓝图；真正的 Swing 面板
 * （`ContentPanel`）由 `TabbedPane` 在需要显示时创建。蓝图与节点一一对应，
 * 因此 [equals]/[hashCode] 都委托给 [node]。
 *
 * **为什么不是 `data class`**：节点是树中的身份对象，且蓝图有可变的 UI 状态位，
 * 自动生成的 `copy`/`componentN` 语义没有意义。
 *
 * **命名说明**：`isXxx` 布尔属性在 Kotlin 中生成的 getter 就是 `isXxx()`、
 * setter 为 `setXxx(...)`，与原 Java 的 JVM 表面完全一致。
 */
class TabBlueprint(node: JNode) {

	/** 该蓝图对应的树节点。 */
	val node: JNode = requireNotNull(node)

	/** 对应的内容面板是否已创建。 */
	var isCreated: Boolean = false

	/** 是否已置顶。 */
	var isPinned: Boolean = false

	/** 是否已加书签。 */
	var isBookmarked: Boolean = false

	/** 是否隐藏（不显示在标签栏，但保留在控制器中）。 */
	var isHidden: Boolean = false

	/** 是否为临时预览标签页。 */
	var isPreviewTab: Boolean = false

	/** 该节点是否支持快速标签页（QuickTabs）。 */
	fun supportsQuickTabs(): Boolean = node.supportsQuickTabs()

	/**
	 * 是否仍被引用（当前仅书签算引用）。
	 * 未被引用的隐藏标签页会被真正关闭。
	 */
	val isReferenced: Boolean get() = isBookmarked

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is TabBlueprint) {
			return false
		}
		return node == other.node
	}

	override fun hashCode(): Int = node.hashCode()

	override fun toString(): String = "TabBlueprint{" + "node=" +
		node + ", pinned=" +
		isPinned + ", bookmarked=" +
		isBookmarked + ", hidden=" +
		isHidden + ", previewTab=" +
		isPreviewTab + '}'
}
