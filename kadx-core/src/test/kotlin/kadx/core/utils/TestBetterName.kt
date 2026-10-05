package kadx.core.utils

import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

class TestBetterName {

	@Deprecated("Test-only helper")
	@Test
	fun test() {
		expectFirst("color_main", "t0")
		expectFirst("done", "oOo0oO0o")
	}

	@Deprecated("Test-only helper")
	private fun expectFirst(first: String, second: String) {
		val best = BetterName.compareAndGet(first, second)
		assertThat(best)
			.`as` { String.format("'%s'=%d, '%s'=%d", first, BetterName.calcRating(first), second, BetterName.calcRating(second)) }
			.isEqualTo(first)
	}
}
