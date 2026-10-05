package kadx.api.plugins.events.types

import kadx.api.metadata.ICodeNodeRef
import kadx.api.plugins.events.IKadxEvent
import kadx.api.plugins.events.KadxEventType
import kadx.api.plugins.events.KadxEvents

/**
 * 「用户重命名节点」事件。
 *
 * **做什么**：携带被重命名的节点、旧名字与新名字；GUI 监听该事件后应用重命名。
 *
 * **为什么保留显式 getter/setter**：这是插件/GUI 公共 API，kadx-gui 的 Java 代码
 * （RenameService、RenameDialog）按 `getXxx()` / `setXxx()` 调用。
 * 注意本类不是 `data class`：它按引用标识使用，且 `renameNode` 是可变的。
 */
class NodeRenamedByUser(
	private val node: ICodeNodeRef,
	private val oldName: String,
	private val newName: String,
) : IKadxEvent {

	/**
	 * 可选的 `JRenameNode` 实例。
	 */
	private var renameNode: Any? = null

	/**
	 * 是否请求把名字重置为原始值。
	 */
	private var resetName: Boolean = false

	fun getNode(): ICodeNodeRef = node

	fun getOldName(): String = oldName

	fun getNewName(): String = newName

	/** 返回可选的 `JRenameNode`，未设置时为 null。 */
	fun getRenameNode(): Any? = renameNode

	fun setRenameNode(renameNode: Any?) {
		this.renameNode = renameNode
	}

	fun isResetName(): Boolean = resetName

	fun setResetName(resetName: Boolean) {
		this.resetName = resetName
	}

	override fun getType(): KadxEventType<NodeRenamedByUser> = KadxEvents.NODE_RENAMED_BY_USER

	override fun toString(): String = "NodeRenamedByUser{" + node +
		", '" + oldName + "' -> '" + newName + '\'' +
		(if (resetName) ", reset name" else "") +
		'}'
}
