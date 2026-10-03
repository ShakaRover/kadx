package jadx.api.gui.tree

import jadx.api.metadata.ICodeNodeRef
import javax.swing.Icon
import javax.swing.tree.TreeNode

/**
 * GUI 树节点接口：在 Swing [TreeNode] 之上补充 jadx 需要的展示与关联信息。
 *
 * **做什么**：jadx-gui 的树（类 / 方法 / 资源等）统一实现本接口，插件据此
 * 获取节点标识、标题、图标，以及节点对应的代码位置引用。
 *
 * **为什么保持接口方法形态**：本接口由 jadx-gui / 插件（Java）实现，方法名必须与
 * 原 Java 一致（`getID` / `getName` / `getIcon` / `getCodeNodeRef`）。
 */
interface ITreeNode : TreeNode {

	/** 与语言环境无关的节点标识。 */
	fun getID(): String

	/**
	 * 节点标题；无标题时返回 `null`（例如未关联 Java 节点的占位节点）。
	 *
	 * 说明：jadx-gui 的 `JNode` 在无 [jadx.api.JavaNode] 时返回 null，
	 * 因此这里如实声明为可空，避免 Kotlin 侧覆写类型不匹配。
	 */
	fun getName(): String?

	/** 节点图标；无图标时返回 `null`（例如文本占位节点）。 */
	fun getIcon(): Icon?

	/** 关联的代码节点引用；无关联时返回 `null`。 */
	fun getCodeNodeRef(): ICodeNodeRef?
}
