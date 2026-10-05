package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * null 内联：对 null 引用的字段访问应内联为 `t1.t2.l`。
 */
class TestNullInline : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestNullInlineFixture.TestCls::class.java))
			.code()
			.containsOne("Long.valueOf(t1.t2.l);")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestNullInlineFixture.TestCls::class.java)
	}
}
