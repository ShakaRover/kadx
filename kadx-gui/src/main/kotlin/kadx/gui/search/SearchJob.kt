package kadx.gui.search

import kadx.gui.treemodel.JNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 单个搜索提供者的执行包装。
 *
 * **做什么**：把 [ISearchProvider] 循环包装成一个 [Runnable]，交给
 * `TaskExecutor` 并行执行。每次拿到结果就交给 [SearchTask.addResult]；
 * 一旦结果数达到上限或被取消，`addResult` 返回 `true`，循环随即结束。
 *
 * **为什么不是 `data class`**：它持有搜索任务与提供者，按身份参与任务调度。
 */
class SearchJob(
	private val searchTask: SearchTask,
	private val provider: ISearchProvider,
) : Runnable {

	override fun run() {
		while (true) {
			try {
				val result: JNode = provider.next(searchTask) ?: return
				if (searchTask.addResult(result)) {
					return
				}
			} catch (e: Exception) {
				LOG.warn("Search error, provider: {}", provider.javaClass.simpleName, e)
				return
			}
		}
	}

	/** 返回本 job 对应的搜索提供者（[SearchTask] 用它汇总进度）。 */
	fun getProvider(): ISearchProvider = provider

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SearchJob::class.java)
	}
}
