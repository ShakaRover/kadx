package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 非法代码（可反编译）：对 `null` 解引用、`synchronized(null)` 等非法字节码
 * 不应导致崩溃，反编译应尽量保持原样。
 */
class TestWrongCode2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestWrongCode2Fixture.TestCls::class.java))
			.code()
			.containsOne("return a.str;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestWrongCode2Fixture.TestCls::class.java)
	}
}
