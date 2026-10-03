package jadx.gui.treemodel

/**
 * 可编辑节点基类。
 *
 * **做什么**：用于“保存修改后的内容”的节点（例如 smali 文件）。
 * 维护一个 `changed` 脏标记，并在状态变化时通知监听者。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
abstract class JEditableNode : JNode() {

	/** 内容是否已被修改（脏标记）。 */
	@Volatile
	private var changed = false

	private val changeListeners: MutableList<(Boolean) -> Unit> = ArrayList()

	/** 保存新内容。 */
	abstract fun save(newContent: String)

	override fun isEditable(): Boolean = true

	fun isChanged(): Boolean = changed

	fun setChanged(changed: Boolean) {
		if (this.changed != changed) {
			this.changed = changed
			for (changeListener in changeListeners) {
				changeListener(changed)
			}
		}
	}

	/** 注册变更监听器，并立即用当前状态回调一次。 */
	fun addChangeListener(listener: (Boolean) -> Unit) {
		changeListeners.add(listener)
		listener(changed)
	}
}
