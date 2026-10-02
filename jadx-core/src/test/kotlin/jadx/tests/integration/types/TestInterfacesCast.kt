package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 接口强转：`(Runnable) closeable` 这类跨接口强转应保留。
 */
class TestInterfacesCast : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestInterfacesCastFixture.TestCls::class.java))
			.code()
			.containsOne("return (Runnable) closeable;")
	}
}
