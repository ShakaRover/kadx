package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器中三元赋值给 `Runnable` 字段：`this.runnable = b ? this : makeRunnable();`。
 */
class TestTypeResolver6a : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver6aFixture.TestCls::class.java))
			.code()
			.containsOne("this.runnable = b ? this : makeRunnable();")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestTypeResolver6aFixture.TestCls::class.java)
	}
}
