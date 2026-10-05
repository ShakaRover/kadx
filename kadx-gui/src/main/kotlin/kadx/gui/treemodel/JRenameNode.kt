package kadx.gui.treemodel

import kadx.api.JavaNode
import kadx.api.data.ICodeRename
import kadx.gui.ui.MainWindow
import javax.swing.Icon

/**
 * 可重命名节点的接口。
 *
 * **做什么**：类 / 方法 / 字段 / 变量 / 包等树节点实现本接口后，就能参与
 * “重命名”对话框与重命名流程（生成 [ICodeRename]、校验新名字、刷新引用等）。
 *
 * **为什么这样写**：这是 Java（`RenameDialog` / `RenameService` / `JRenamePackage`）
 * 与 Kotlin 共用的接口，方法名与 JVM 签名必须保持不变。
 * `getName` / `getIcon` 声明为可空，因为部分实现（如变量、占位节点）可能没有名称或图标。
 */
interface JRenameNode {

	/** 关联的 Java 节点视图。 */
	fun getJavaNode(): JavaNode

	/** 用于对话框标题的字符串。 */
	fun getTitle(): String

	/** 节点名称；可能为 `null`。 */
	fun getName(): String?

	/** 节点图标；可能为 `null`。 */
	fun getIcon(): Icon?

	/** 当前节点是否允许重命名。 */
	fun canRename(): Boolean

	/**
	 * 真正参与重命名的节点。
	 * 例如构造函数重命名会替换为其所在类；默认返回自身。
	 */
	fun replace(): JRenameNode = this

	/** 根据新名字构造重命名记录（`renames` 中已存在的冲突项可能被移除）。 */
	fun buildCodeRename(newName: String, renames: MutableSet<ICodeRename>): ICodeRename

	/** 校验新名字是否合法。 */
	fun isValidName(newName: String): Boolean

	/** 移除别名，恢复原始名称。 */
	fun removeAlias()

	/** 收集需要刷新的 Java 节点。 */
	fun addUpdateNodes(toUpdate: MutableList<JavaNode>)

	/** 重命名完成后刷新界面。 */
	fun reload(mainWindow: MainWindow)
}
