package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 浮点比较：同类型直接比较，跨类型（float 与 double）需显式提升。
 */
class TestCmpOp2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestCmpOp2Fixture.TestCls::class.java))
			.code()
			.contains("return a > b;")
			.contains("return ((double) c) < d;")
	}
}
