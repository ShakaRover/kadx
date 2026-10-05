package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 通过子类调用继承的静态方法：应保留 `B.a()` 而非改写为 `A.a()`。
 */
class TestInheritedStaticInvoke : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestInheritedStaticInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("return B.a();")
	}
}
