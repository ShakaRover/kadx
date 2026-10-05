package kadx.gui.ui.hexviewer.search

/**
 * 一次搜索请求的全部参数。
 *
 * **做什么**：把「搜索条件、起始位置、是否从光标处开始、是否区分大小写、
 * 单次还是全部匹配、向前还是向后」打包在一起，供搜索服务使用。
 *
 * 这些 getter/setter 保留显式函数形式，Java 与 Kotlin 调用方都能零改动调用。
 *
 * 移植自 ExBin Project（Apache-2.0）。
 */
class SearchParameters {

	private var condition: SearchCondition = SearchCondition()
	private var startPosition: Long = 0
	private var searchFromCursor: Boolean = false
	private var matchCase: Boolean = true
	private var matchMode: MatchMode = MatchMode.MULTIPLE
	private var searchDirection: SearchDirection = SearchDirection.FORWARD

	fun getCondition(): SearchCondition = condition

	fun setCondition(condition: SearchCondition) {
		this.condition = condition
	}

	fun getStartPosition(): Long = startPosition

	fun setStartPosition(startPosition: Long) {
		this.startPosition = startPosition
	}

	val isSearchFromCursor: Boolean get() = searchFromCursor

	fun setSearchFromCursor(searchFromCursor: Boolean) {
		this.searchFromCursor = searchFromCursor
	}

	val isMatchCase: Boolean get() = matchCase

	fun setMatchCase(matchCase: Boolean) {
		this.matchCase = matchCase
	}

	fun getMatchMode(): MatchMode = matchMode

	fun setMatchMode(matchMode: MatchMode) {
		this.matchMode = matchMode
	}

	fun getSearchDirection(): SearchDirection = searchDirection

	fun setSearchDirection(searchDirection: SearchDirection) {
		this.searchDirection = searchDirection
	}

	/** 从另一个参数对象复制全部字段（浅拷贝，condition 共享同一引用）。 */
	fun setFromParameters(searchParameters: SearchParameters) {
		condition = searchParameters.getCondition()
		startPosition = searchParameters.getStartPosition()
		searchFromCursor = searchParameters.isSearchFromCursor
		matchCase = searchParameters.isMatchCase
		matchMode = searchParameters.getMatchMode()
		searchDirection = searchParameters.getSearchDirection()
	}

	enum class SearchDirection {
		FORWARD,
		BACKWARD,
	}

	enum class MatchMode {
		SINGLE,
		MULTIPLE,
		;

		companion object {
			/** 把「是否全部匹配」布尔值转换成枚举。 */
			fun fromBoolean(multipleMatches: Boolean): MatchMode = if (multipleMatches) MULTIPLE else SINGLE
		}
	}
}
