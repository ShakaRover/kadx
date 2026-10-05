package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
