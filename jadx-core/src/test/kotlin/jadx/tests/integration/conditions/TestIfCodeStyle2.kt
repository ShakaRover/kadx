package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue #2052
 */
class TestIfCodeStyle2 : SmaliTest() {

	@Test
	fun testSmali() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(1, "} else if (")
			.countString(1, "} else {")
			.countString(19, "return ")
	}
}
