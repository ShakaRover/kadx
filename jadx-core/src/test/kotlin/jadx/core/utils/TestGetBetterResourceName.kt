package jadx.core.utils

import org.assertj.core.api.AbstractStringAssert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TestGetBetterResourceName {

	@Test
	fun testGoodNamesVsSyntheticNames() {
		assertThatBetterResourceName("color_main", "t0").isEqualTo("color_main")
		assertThatBetterResourceName("done", "oOo0oO0o").isEqualTo("done")
	}

	/**
	 * Tests [BetterName.getBetterResourceName] on equally good names.
	 * In this case, according to the documentation, the method should return the first argument.
	 *
	 * @see BetterName.getBetterResourceName
	 */
	@Test
	fun testEquallyGoodNames() {
		assertThatBetterResourceName("AAAA", "BBBB").isEqualTo("AAAA")
		assertThatBetterResourceName("BBBB", "AAAA").isEqualTo("BBBB")

		assertThatBetterResourceName("Theme.AppCompat.Light", "Theme_AppCompat_Light")
			.isEqualTo("Theme.AppCompat.Light")
		assertThatBetterResourceName("Theme_AppCompat_Light", "Theme.AppCompat.Light")
			.isEqualTo("Theme_AppCompat_Light")
	}

	private fun assertThatBetterResourceName(firstName: String, secondName: String): AbstractStringAssert<*> = assertThat(BetterName.getBetterResourceName(firstName, secondName))
}
