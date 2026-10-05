package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型父类构造器调用：`super(e)` 与 `super(s)` 的重载选择应正确还原。
 */
class TestSuperInvokeWithGenerics : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSuperInvokeWithGenericsFixture.TestCls::class.java))
			.code()
			.containsOne("super(e);")
			.containsOne("super(s);")
	}
}
