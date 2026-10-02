package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 位或运算用于布尔条件：`(a | b)` 的不同比较形式应分别还原为 `||` 或 `&&` 的否定形式。
 */
class TestBitwiseOr : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestBitwiseOrFixture.TestCls::class.java))
			.code()
			.containsOne("if (this.a || this.b) {")
	}

	@Test
	fun test2() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestBitwiseOrFixture.TestCls2::class.java))
			.code()
			.containsOne("if (!this.a && !this.b) {")
	}

	@Test
	fun test3() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestBitwiseOrFixture.TestCls3::class.java))
			.code()
			.containsOne("if (!this.a && !this.b) {")
	}

	@Test
	fun test4() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestBitwiseOrFixture.TestCls4::class.java))
			.code()
			.containsOne("if (this.a || this.b) {")
	}
}
