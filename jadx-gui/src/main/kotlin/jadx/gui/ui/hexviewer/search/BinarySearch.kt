package jadx.gui.ui.hexviewer.search

import jadx.gui.ui.hexviewer.HexSearchBar
import jadx.gui.ui.hexviewer.search.service.BinarySearchService
import jadx.gui.utils.NLS
import org.exbin.auxiliary.binary_data.EditableBinaryData
import org.exbin.auxiliary.binary_data.array.ByteArrayEditableData

/**
 * 十六进制视图的「搜索协调器」。
 *
 * **做什么**：
 * - 接收搜索框 [HexSearchBar] 发出的各种操作（查找、上一个、下一个、关闭等）；
 * - 用「延迟线程 + 搜索线程」两级线程避免用户连续输入时反复触发搜索
 *   （[InvokeSearchThread] 先睡 [DEFAULT_DELAY] 毫秒，被打断就不搜）；
 * - 把结果通过 [BinarySearchService.SearchStatusListener] 回调给搜索框更新提示。
 *
 * **线程模型**：保持原 Swing 模型，纯 Java 线程（[Thread]）+ `interrupt()` 取消，
 * 不使用协程。
 *
 * 移植自 ExBin Project（Apache-2.0）。
 */
class BinarySearch(private var binarySearchPanel: HexSearchBar) {

	private var invokeSearchThread: InvokeSearchThread? = null
	private var searchThread: SearchThread? = null

	private var currentSearchOperation = SearchOperation.FIND
	private var currentSearchDirection = SearchParameters.SearchDirection.FORWARD
	private val currentSearchParameters = SearchParameters()
	private var foundMatches = BinarySearchService.FoundMatches()

	private var binarySearchService: BinarySearchService? = null
	private val searchStatusListener: BinarySearchService.SearchStatusListener

	init {
		searchStatusListener = object : BinarySearchService.SearchStatusListener {
			override fun setStatus(foundMatches: BinarySearchService.FoundMatches, matchMode: SearchParameters.MatchMode) {
				this@BinarySearch.foundMatches = foundMatches
				when (foundMatches.getMatchesCount()) {
					0 -> binarySearchPanel.setInfoLabel(NLS.str("search.match_not_found"))

					1 -> binarySearchPanel.setInfoLabel(
						if (matchMode == SearchParameters.MatchMode.MULTIPLE) NLS.str("search.single_match") else NLS.str("search.match_found"),
					)

					else -> binarySearchPanel.setInfoLabel(
						String.format(NLS.str("search.match_of"), foundMatches.getMatchPosition() + 1, foundMatches.getMatchesCount()),
					)
				}
				updateMatchStatus()
			}

			override fun clearStatus() {
				binarySearchPanel.setInfoLabel("")
				this@BinarySearch.foundMatches = BinarySearchService.FoundMatches()
				updateMatchStatus()
			}

			private fun updateMatchStatus() {
				val matchesCount = this@BinarySearch.foundMatches.getMatchesCount()
				val matchPosition = this@BinarySearch.foundMatches.getMatchPosition()
				binarySearchPanel.updateMatchCount(
					matchesCount > 0,
					matchesCount > 1 && matchPosition > 0,
					matchPosition < matchesCount - 1,
				)
			}
		}
		binarySearchPanel.setControl(object : HexSearchBar.Control {
			override fun prevMatch() {
				this@BinarySearch.foundMatches.prev()
				val service = binarySearchService ?: return
				service.setMatchPosition(this@BinarySearch.foundMatches.getMatchPosition())
				searchStatusListener.setStatus(this@BinarySearch.foundMatches, service.getLastSearchParameters().getMatchMode())
			}

			override fun nextMatch() {
				this@BinarySearch.foundMatches.next()
				val service = binarySearchService ?: return
				service.setMatchPosition(this@BinarySearch.foundMatches.getMatchPosition())
				searchStatusListener.setStatus(this@BinarySearch.foundMatches, service.getLastSearchParameters().getMatchMode())
			}

			override fun performEscape() {
				cancelSearch()
				close()
				clearSearch()
			}

			override fun performFind() {
				this@BinarySearch.invokeSearch(SearchOperation.FIND)
			}

			override fun notifySearchChanged() {
				if (this@BinarySearch.currentSearchOperation == SearchOperation.FIND) {
					this@BinarySearch.invokeSearch(SearchOperation.FIND)
				}
			}

			override fun notifySearchChanging() {
				if (this@BinarySearch.currentSearchOperation != SearchOperation.FIND) {
					return
				}

				val condition = this@BinarySearch.currentSearchParameters.getCondition()
				val updatedSearchCondition = binarySearchPanel.searchParameters.getCondition()

				when (updatedSearchCondition.getSearchMode()) {
					SearchCondition.SearchMode.TEXT -> {
						val searchText = updatedSearchCondition.getSearchText()
						if (searchText.isEmpty()) {
							condition.setSearchText(searchText)
							clearSearch()
							return
						}
						if (searchText == condition.getSearchText()) {
							return
						}
						condition.setSearchText(searchText)
					}

					SearchCondition.SearchMode.BINARY -> {
						val searchData = updatedSearchCondition.getBinaryData() as EditableBinaryData?
						if (searchData == null || searchData.isEmpty()) {
							condition.setBinaryData(null)
							clearSearch()
							return
						}
						if (searchData == condition.getBinaryData()) {
							return
						}
						val data = ByteArrayEditableData()
						data.insert(0L, searchData)
						condition.setBinaryData(data)
					}
				}
				this@BinarySearch.invokeSearch(SearchOperation.FIND, DEFAULT_DELAY)
			}

			override fun getSearchDirection(): SearchParameters.SearchDirection = this@BinarySearch.currentSearchDirection

			override fun close() {
				cancelSearch()
				clearSearch()
			}
		})
	}

	fun setBinarySearchService(binarySearchService: BinarySearchService) {
		this.binarySearchService = binarySearchService
	}

	fun setTargetComponent(targetComponent: HexSearchBar) {
		binarySearchPanel = targetComponent
	}

	fun getSearchStatusListener(): BinarySearchService.SearchStatusListener = searchStatusListener

	private fun invokeSearch(searchOperation: SearchOperation) {
		invokeSearch(searchOperation, binarySearchPanel.searchParameters, 0)
	}

	private fun invokeSearch(searchOperation: SearchOperation, delay: Int) {
		invokeSearch(searchOperation, binarySearchPanel.searchParameters, delay)
	}

	private fun invokeSearch(searchOperation: SearchOperation, searchParameters: SearchParameters) {
		invokeSearch(searchOperation, searchParameters, 0)
	}

	private fun invokeSearch(searchOperation: SearchOperation, searchParameters: SearchParameters, delay: Int) {
		invokeSearchThread?.interrupt()
		invokeSearchThread = InvokeSearchThread(delay)
		currentSearchOperation = searchOperation
		currentSearchParameters.setFromParameters(searchParameters)
		invokeSearchThread?.start()
	}

	fun cancelSearch() {
		invokeSearchThread?.interrupt()
		searchThread?.interrupt()
	}

	fun clearSearch() {
		val condition = currentSearchParameters.getCondition()
		condition.clear()
		binarySearchPanel.clearSearch()
		binarySearchService?.clearMatches()
		searchStatusListener.clearStatus()
	}

	val panel: HexSearchBar get() = binarySearchPanel

	fun dataChanged() {
		binarySearchService?.clearMatches()
		invokeSearch(currentSearchOperation, DEFAULT_DELAY)
	}

	/** 延迟线程：先睡一会儿，若期间被 interrupt 就放弃本次搜索。 */
	private inner class InvokeSearchThread(private val delay: Int) : Thread("InvokeSearchThread") {

		override fun run() {
			try {
				Thread.sleep(delay.toLong())
				searchThread?.interrupt()
				val thread = SearchThread()
				searchThread = thread
				thread.start()
			} catch (ex: InterruptedException) {
				// 被取消，不再搜索
			}
		}
	}

	/** 真正执行搜索的线程。 */
	private inner class SearchThread : Thread("SearchThread") {

		override fun run() {
			val service = binarySearchService
			when (currentSearchOperation) {
				SearchOperation.FIND -> service?.performFind(currentSearchParameters, searchStatusListener)
				SearchOperation.FIND_AGAIN -> service?.performFindAgain(searchStatusListener)
				else -> throw UnsupportedOperationException("Not supported yet.")
			}
		}
	}

	private enum class SearchOperation {
		FIND,
		FIND_AGAIN,
	}

	companion object {
		private const val DEFAULT_DELAY = 500
	}
}
