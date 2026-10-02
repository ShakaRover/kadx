package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
