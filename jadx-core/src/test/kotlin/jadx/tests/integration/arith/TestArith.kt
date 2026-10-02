package jadx.tests.integration.arith

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 算术自增/自赋值：`a += 2;` / `a++;` 的还原（部分场景当前尚未实现）。
 */
class TestArith : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArithFixture.TestCls::class.java))
			.code()
	}

	@Test
	@NotYetImplemented
	fun test2() {
		JadxAssertions.assertThat(getClassNode(TestArithFixture.TestCls::class.java))
			.code()
			.contains("a += 2;")
			.contains("a++;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestArithFixture.TestCls::class.java))
			.code()
	}

	@Test
	@NotYetImplemented
	fun testNoDebug2() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestArithFixture.TestCls::class.java))
			.code()
			.contains("i += 2;")
			.contains("i++;")
	}
}
