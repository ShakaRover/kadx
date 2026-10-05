package kadx.tests.integration.arith

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 实例字段的自赋值：`this.a.f += n;` / `this.a.f *= n;` 应合并为复合赋值。
 */
class TestFieldIncrement2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestFieldIncrement2Fixture.TestCls::class.java))
			.code()
			.contains("this.a.f += n;")
			.contains("this.a.f *= n;")
	}
}
