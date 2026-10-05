package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 变参方法在静态初始化中的调用（已知部分还原缺陷）。
 */
class TestVarArg2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestVarArg2Fixture.TestCls::class.java))
			.code()
			.containsOne("isValid(\"test\")") // TODO: .containsOne("b1 && isValid(\"test\")");
	}
}
