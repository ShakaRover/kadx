package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器中三元赋值给 `Object` 字段：`this.obj = b ? this : makeObj();`。
 */
class TestTypeResolver6 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver6Fixture.TestCls::class.java))
			.code()
			.containsOne("this.obj = b ? this : makeObj();")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestTypeResolver6Fixture.TestCls::class.java)
	}
}
