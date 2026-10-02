package jadx.tests.integration.loops

import jadx.tests.api.RaungTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
