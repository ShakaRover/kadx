package kadx.tests.integration.names

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 方法名与类名相同：`Bug()` 方法与 `Bug` 类同名时，调用应正确区分构造器与方法。
 */
class TestSameMethodsNames : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSameMethodsNamesFixture.TestCls::class.java))
			.code()
			.containsOne("new Bug().Bug();")
	}
}
