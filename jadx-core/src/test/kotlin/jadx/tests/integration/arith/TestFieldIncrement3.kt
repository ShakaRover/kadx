package jadx.tests.integration.arith

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
