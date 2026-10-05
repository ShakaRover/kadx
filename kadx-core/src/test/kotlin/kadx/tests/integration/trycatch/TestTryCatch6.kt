package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 无限循环中的 try/catch：有无调试信息时都应保留 try 结构。
 */
class TestTryCatch6 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTryCatch6Fixture.TestCls::class.java))
			.code()
			.containsOne("try {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestTryCatch6Fixture.TestCls::class.java))
			.code()
			.containsOne("try {")
	}
}
