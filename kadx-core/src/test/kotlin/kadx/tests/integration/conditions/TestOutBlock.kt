package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue #2384
 */
class TestOutBlock : SmaliTest() {

	@Test
	fun test() {
		allowWarnInCode()
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("setContentView")
	}
}
