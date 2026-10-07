package kadx.gui.search

/**
 * 搜索结果数量策略：分页上限 + 硬上限。
 *
 * **做什么**：统计搜索产出的结果数，并判断是否应当停止。
 * - **分页上限**（[onResult] 的 `pageLimit`，来自设置的 `searchResultsPerPage`）：
 *   达到后暂停，用户点“加载更多”可**从游标续跑**（[resetPage] 后重新计数）；
 * - **硬上限**（[hardLimit]，即 [SearchTask.MAX_RESULTS_LIMIT]）：整个任务累计达到后
 *   终止，即使用户选了“加载全部”（`pageLimit = 0`）也不会让结果集无界增长。
 *
 * **为什么单独成类**：`SearchTask` 需要 `MainWindow` 才能构造，无法在单测里直接驱动；
 * 把这段纯计数/判定逻辑抽出来才能用固定假数据做确定性测试。
 *
 * **线程模型**：调用方（`SearchTask.addResult`）已加锁，本类自身不做同步。
 */
internal class SearchResultsLimiter(
	private val hardLimit: Int = SearchTask.MAX_RESULTS_LIMIT,
) {

	/** 本页已产出结果数。 */
	var pageCount: Int = 0
		private set

	/** 整个任务累计产出结果数（跨分页）。 */
	var totalCount: Int = 0
		private set

	/** 是否因触达 [hardLimit] 而停止（终态，不随分页重置）。 */
	var isHardLimitReached: Boolean = false
		private set

	/** 开始新一页：只重置分页计数，累计计数与硬上限状态保持不变。 */
	fun resetPage() {
		pageCount = 0
	}

	/**
	 * 记录一个结果。
	 *
	 * @param pageLimit 本页上限；`0` 表示不分页（“加载全部”）
	 * @return `true` 表示应停止搜索
	 */
	fun onResult(pageLimit: Int): Boolean {
		pageCount++
		totalCount++
		if (totalCount >= hardLimit) {
			isHardLimitReached = true
			return true
		}
		if (pageLimit != 0 && pageCount >= pageLimit) {
			return true
		}
		return false
	}
}
