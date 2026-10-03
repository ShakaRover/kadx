package jadx.gui.ui.codearea.theme

/**
 * 主题的「ID + 显示名」二元组，用于填充设置界面的主题下拉框。
 *
 * **相等性**：只按 [id] 比较（`equals` / `hashCode` 都基于 id），
 * 显示名 [name] 仅用于 `toString()` 展示。这样同一个 ID 的主题不会被重复添加。
 *
 * 保留显式的 `getId()` / `getName()` 函数，既兼容 Java 调用方，
 * 也兼容已有 Kotlin 调用方写下的 `selected.getId()`。
 */
class ThemeIdAndName(private val id: String, private val name: String) {

	fun getId(): String = id

	fun getName(): String = name

	override fun equals(other: Any?): Boolean {
		if (other !is ThemeIdAndName) {
			return false
		}
		return id == other.id
	}

	override fun hashCode(): Int = id.hashCode()

	override fun toString(): String = name
}
