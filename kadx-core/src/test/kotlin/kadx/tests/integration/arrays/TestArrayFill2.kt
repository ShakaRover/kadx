package kadx.tests.integration.arrays

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 数组填充：常量与简单表达式可合并为数组字面量；带副作用（`a++`）的暂不支持（已知未实现）。
 */
class TestArrayFill2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestArrayFill2Fixture.TestCls::class.java))
			.code()
			.contains("return new int[]{1, a + 1, 2};")
	}

	@Test
	@NotYetImplemented
	fun test2() {
		KadxAssertions.assertThat(getClassNode(TestArrayFill2Fixture.TestCls2::class.java))
			.code()
			.contains("return new int[]{1, a++, a * 2};")
	}
}
