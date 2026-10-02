package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 多参数构造器调用：局部变量与参数顺序应完整还原。
 */
class TestInvoke1 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInvoke1Fixture.TestCls::class.java))
			.code()
			.containsOne("C pkg = new C(id, name, types, keys);")
	}
}
