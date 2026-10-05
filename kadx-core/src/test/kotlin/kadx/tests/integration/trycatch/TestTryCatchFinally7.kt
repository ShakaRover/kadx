package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try/catch/finally 中对布尔结果的赋值：不应生成多余的 `throw th;` 语句。
 */
class TestTryCatchFinally7 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryCatchFinally7Fixture.TestCls::class.java))
			.code()
			.contains("try {")
			.contains("exc(obj);")
			.contains("} catch (Exception e) {")
			.doesNotContain("throw th;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestTryCatchFinally7Fixture.TestCls::class.java))
			.code()
			.doesNotContain("throw th;")
	}
}
