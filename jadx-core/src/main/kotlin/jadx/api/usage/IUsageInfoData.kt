package jadx.api.usage

import jadx.core.dex.nodes.ClassNode

/**
 * 使用信息数据：保存一次 usage 分析的结果，并能把结果写回 AST 节点。
 *
 * **做什么**：
 * - [apply]：把整棵树的 usage 结果一次性写回各节点；
 * - [applyForClass]：只写回单个类（增量重算用）；
 * - [visitUsageData]：把结果导出给外部访问者（例如持久化到磁盘缓存）。
 *
 * **为什么保持接口方法形态**：本接口由 jadx-gui（Java）实现，方法名必须与原 Java 一致。
 */
interface IUsageInfoData {

	/** 把使用信息写回所有相关节点。 */
	fun apply()

	/** 只把使用信息写回 [cls] 及其成员。 */
	fun applyForClass(cls: ClassNode)

	/** 把使用信息逐个回调给 [visitor]。 */
	fun visitUsageData(visitor: IUsageInfoVisitor)
}
