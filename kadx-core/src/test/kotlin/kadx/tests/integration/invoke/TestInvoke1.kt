package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 多参数构造器调用：局部变量与参数顺序应完整还原。
 */
class TestInvoke1 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInvoke1Fixture.TestCls::class.java))
			.code()
			.containsOne("C pkg = new C(id, name, types, keys);")
	}
}
