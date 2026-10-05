package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 宽变量（long/double）作为方法参数：调用处字面量应带正确后缀。
 */
class TestInvokeWithWideVars : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestInvokeWithWideVarsFixture.TestCls::class.java))
			.code()
			.containsOne("return call(1, 2L);")
			.containsOne("return rangeCall(1L, 2, 3.0d, (byte) 4);")
	}
}
