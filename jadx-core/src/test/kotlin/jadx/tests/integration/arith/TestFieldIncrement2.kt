package jadx.tests.integration.arith

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 实例字段的自赋值：`this.a.f += n;` / `this.a.f *= n;` 应合并为复合赋值。
 */
class TestFieldIncrement2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestFieldIncrement2Fixture.TestCls::class.java))
			.code()
			.contains("this.a.f += n;")
			.contains("this.a.f *= n;")
	}
}
