package kadx.gui.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [SearchResultsLimiter] 的数量策略测试。
 *
 * 覆盖两件事：分页上限达到后**可续跑**（`resetPage` 后重新计一页），
 * 以及硬上限对“加载全部”（`pageLimit = 0`）也生效——这是结果集无界增长的最后一道闸。
 */
class SearchResultsLimiterTest {

	@Test
	fun pageLimitStopsAndCanResume() {
		val limiter = SearchResultsLimiter(hardLimit = 1000)

		// 第一页：每页 3 条
		assertThat(limiter.onResult(pageLimit = 3)).isFalse()
		assertThat(limiter.onResult(pageLimit = 3)).isFalse()
		assertThat(limiter.onResult(pageLimit = 3)).isTrue()
		assertThat(limiter.pageCount).isEqualTo(3)
		assertThat(limiter.totalCount).isEqualTo(3)
		assertThat(limiter.isHardLimitReached).isFalse()

		// “加载更多”：重置分页计数后应能再取一页
		limiter.resetPage()
		assertThat(limiter.pageCount).isZero()
		assertThat(limiter.totalCount).isEqualTo(3)

		assertThat(limiter.onResult(pageLimit = 3)).isFalse()
		assertThat(limiter.onResult(pageLimit = 3)).isFalse()
		assertThat(limiter.onResult(pageLimit = 3)).isTrue()
		assertThat(limiter.totalCount).isEqualTo(6)
	}

	@Test
	fun hardLimitAlsoAppliesToLoadAll() {
		val limiter = SearchResultsLimiter(hardLimit = 5)
		// pageLimit = 0 表示“加载全部”
		for (i in 1..4) {
			assertThat(limiter.onResult(pageLimit = 0)).isFalse()
		}
		assertThat(limiter.onResult(pageLimit = 0)).isTrue()
		assertThat(limiter.isHardLimitReached).isTrue()
		assertThat(limiter.totalCount).isEqualTo(5)
	}

	@Test
	fun hardLimitIsCumulativeAcrossPages() {
		val limiter = SearchResultsLimiter(hardLimit = 5)
		repeat(2) {
			limiter.resetPage()
			assertThat(limiter.onResult(pageLimit = 2)).isFalse()
			assertThat(limiter.onResult(pageLimit = 2)).isTrue()
		}
		// 累计 4 条：硬上限 5 尚未触发
		assertThat(limiter.isHardLimitReached).isFalse()

		limiter.resetPage()
		// 第 5 条：累计达到硬上限，onResult 返回 true
		assertThat(limiter.onResult(pageLimit = 2)).isTrue()
		assertThat(limiter.isHardLimitReached).isTrue()
	}

	@Test
	fun hardLimitIsTerminalAcrossPageReset() {
		val limiter = SearchResultsLimiter(hardLimit = 2)
		assertThat(limiter.onResult(pageLimit = 0)).isFalse()
		assertThat(limiter.onResult(pageLimit = 0)).isTrue()

		limiter.resetPage()
		assertThat(limiter.isHardLimitReached).isTrue()
		assertThat(limiter.totalCount).isEqualTo(2)
	}
}
