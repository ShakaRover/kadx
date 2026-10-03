package jadx.gui.ui.hexviewer.search.service

import jadx.gui.ui.hexviewer.search.SearchParameters

/**
 * 二进制搜索服务接口。
 *
 * **做什么**：在十六进制视图里执行「查找」「再次查找」「设置当前匹配位置」「清除匹配」，
 * 并通过 [SearchStatusListener] 回调把结果状态告知界面。
 *
 * 保持显式函数签名，方便 Java 侧实现与调用（与原来完全一致）。
 */
interface BinarySearchService {

	fun performFind(dialogSearchParameters: SearchParameters, searchStatusListener: SearchStatusListener)

	fun setMatchPosition(matchPosition: Int)

	fun performFindAgain(searchStatusListener: SearchStatusListener)

	fun getLastSearchParameters(): SearchParameters

	fun clearMatches()

	/** 搜索状态回调：搜索完成或清除时通知界面更新提示文字与按钮状态。 */
	interface SearchStatusListener {

		fun setStatus(foundMatches: FoundMatches, matchMode: SearchParameters.MatchMode)

		fun clearStatus()
	}

	/** 匹配结果集合：总匹配数与当前所在匹配序号（从 0 开始，-1 表示无）。 */
	class FoundMatches {

		private var matchesCount: Int
		private var matchPosition: Int

		constructor() {
			matchesCount = 0
			matchPosition = -1
		}

		constructor(matchesCount: Int, matchPosition: Int) {
			if (matchPosition >= matchesCount) {
				throw IllegalStateException("Match position is out of range")
			}
			this.matchesCount = matchesCount
			this.matchPosition = matchPosition
		}

		fun getMatchesCount(): Int = matchesCount

		fun getMatchPosition(): Int = matchPosition

		fun setMatchesCount(matchesCount: Int) {
			this.matchesCount = matchesCount
		}

		fun setMatchPosition(matchPosition: Int) {
			this.matchPosition = matchPosition
		}

		/** 跳到下一个匹配。 */
		fun next() {
			if (matchPosition == matchesCount - 1) {
				throw IllegalStateException("Cannot find next on last match")
			}
			matchPosition++
		}

		/** 跳到上一个匹配。 */
		fun prev() {
			if (matchPosition == 0) {
				throw IllegalStateException("Cannot find previous on first match")
			}
			matchPosition--
		}
	}
}
