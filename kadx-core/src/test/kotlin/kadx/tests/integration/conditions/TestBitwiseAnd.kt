package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 位与运算用于布尔条件：`(a & b)` 的不同比较形式应分别还原为 `&&` 或 `||` 的否定形式。
 */
class TestBitwiseAnd : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestBitwiseAndFixture.TestCls::class.java))
			.code()
			.containsOne("if (this.a && this.b) {")
	}

	@Test
	fun test2() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestBitwiseAndFixture.TestCls2::class.java))
			.code()
			.containsOne("if (!this.a || !this.b) {")
	}

	@Test
	fun test3() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestBitwiseAndFixture.TestCls3::class.java))
			.code()
			.containsOne("if (!this.a || !this.b) {")
	}

	@Test
	fun test4() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestBitwiseAndFixture.TestCls4::class.java))
			.code()
			.containsOne("if (this.a && this.b) {")
	}
}
