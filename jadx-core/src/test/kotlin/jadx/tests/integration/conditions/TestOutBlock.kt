package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
