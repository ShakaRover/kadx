package jadx.tests.integration.names

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 方法名与类名相同：`Bug()` 方法与 `Bug` 类同名时，调用应正确区分构造器与方法。
 */
class TestSameMethodsNames : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestSameMethodsNamesFixture.TestCls::class.java))
			.code()
			.containsOne("new Bug().Bug();")
	}
}
