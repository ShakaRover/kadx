package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
