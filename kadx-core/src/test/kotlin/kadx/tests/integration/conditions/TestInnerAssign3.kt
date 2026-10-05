package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue #820
 */
class TestInnerAssign3 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("(testClass2TestMethod = (testClass1 = null).testMethod()) == null")
			.containsOne("testClass1.testField != null")
	}
}
