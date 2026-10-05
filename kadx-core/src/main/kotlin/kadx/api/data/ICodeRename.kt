package kadx.api.data

/**
 * 用户重命名记录的公共接口（把某个节点/代码元素改成新名字）。
 *
 * **做什么**：与 [ICodeComment] 结构类似——挂载节点引用 + 可选代码引用 + 新名字。
 * 继承 `Comparable` 以支持稳定排序；实现类需保证 [getNodeRef] 非空。
 *
 * **为什么保留显式 getter**：公共 API，kadx-gui / 插件以 Java 或 Kotlin 实现与调用，
 * 显式函数确保 JVM 名与原生 Java 完全一致。
 */
interface ICodeRename : Comparable<ICodeRename> {

	/** 重命名作用的目标节点引用。 */
	fun getNodeRef(): IJavaNodeRef

	/** 可选的代码引用；为 null 表示重命名节点本身（类/方法/字段/包）。 */
	fun getCodeRef(): IJavaCodeRef?

	/** 新的名字。 */
	fun getNewName(): String
}
