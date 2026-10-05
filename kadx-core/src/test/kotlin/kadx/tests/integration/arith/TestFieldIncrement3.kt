package kadx.tests.integration.arith

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 浮点字段运算：`switch` 分支内的坐标计算与向量差值应保持原表达式。
 */
class TestFieldIncrement3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldIncrement3Fixture.TestCls::class.java))
			.code()
			.contains("directVect.x = targetPos.x - newPos.x;")
	}
}
