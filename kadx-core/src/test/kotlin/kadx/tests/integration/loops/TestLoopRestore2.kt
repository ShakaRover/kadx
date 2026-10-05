package kadx.tests.integration.loops

import kadx.tests.api.RaungTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环还原：不可达语句场景下仍应保留恒假循环。
 */
class TestLoopRestore2 : RaungTest() {

	@Test
	fun test() {
		disableCompilation() // unreachable statement
		assertThat(getClassNodeFromRaung())
			.code()
			.containsOne("while (1 == 0) {")
	}
}
