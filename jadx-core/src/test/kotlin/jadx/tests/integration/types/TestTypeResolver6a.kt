package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
