package kadx.gui.search

import kadx.gui.jobs.Cancelable
import kadx.gui.jobs.ITaskProgress
import kadx.gui.treemodel.JNode

/**
 * 搜索结果提供者接口。
 *
 * **做什么**：一次搜索可能由多个“提供者”组成（类名、方法名、字段名、代码内容、
 * 注释、资源等）。每个提供者负责按需产出下一个命中的 [JNode]，并汇报进度。
 *
 * **为什么继承 [ITaskProgress]**：搜索任务需要把各提供者的 `progress()` / `total()`
 * 汇总后显示到进度条上。
 *
 * **为什么保持显式函数形态**：该接口被多个提供者实现（[providers.BaseSearchProvider]
 * 及其子类、`CommentSearchProvider`、`ResourceSearchProvider`、`MergedSearchProvider`），
 * 方法名与 JVM 签名必须与原 Java 完全一致。
 */
interface ISearchProvider : ITaskProgress {

	/**
	 * 返回下一个搜索结果；搜索完成时返回 `null`。
	 *
	 * @param cancelable 用于检查是否已被用户取消
	 */
	fun next(cancelable: Cancelable): JNode?
}
