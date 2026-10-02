package jadx.api.data

/**
 * 可被重命名的节点：jadx 内部 `IDexNode` 等类型实现它，把用户的新名字写回节点。
 */
interface IRenameNode {

	/** 把节点重命名为 [newName]。 */
	fun rename(newName: String)
}
