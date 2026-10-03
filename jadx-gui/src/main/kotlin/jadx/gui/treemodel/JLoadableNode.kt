package jadx.gui.treemodel

import jadx.gui.jobs.IBackgroundTask
import java.util.function.Predicate

/**
 * “按需加载”节点基类。
 *
 * **做什么**：类节点 / 资源节点在树中可能先以占位形式存在，真正的内容在展开或搜索时才加载。
 * 本类在搜索、移除子节点前先调用 [loadNode]，保证操作对象已经就绪。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
abstract class JLoadableNode : JNode() {

	/** 立即加载本节点的内容。 */
	abstract fun loadNode()

	/** 返回用于后台加载的任务；无需加载时返回 `null`。 */
	abstract fun getLoadTask(): IBackgroundTask?

	override fun searchNode(filter: Predicate<JNode>): JNode? {
		loadNode()
		return super.searchNode(filter)
	}

	override fun searchDepthNode(filter: Predicate<JNode>): JNode? {
		loadNode()
		return super.searchDepthNode(filter)
	}

	override fun removeNode(filter: Predicate<JNode>): JNode? {
		loadNode()
		return super.removeNode(filter)
	}

	companion object {
		private const val serialVersionUID = 5543590584166374958L
	}
}
