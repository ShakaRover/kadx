package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型 List 的 for-each 与 `Collections.sort`：应还原 `List<String>` 与增强 for 循环。
 */
class TestGenerics3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestGenerics3Fixture.TestCls::class.java))
			.code()
			.containsOne("List<String> classes")
			.containsOne("for (String cls : classes) {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestGenerics3Fixture.TestCls::class.java))
			.code()
			.containsOne("List<String> classes")
	}
}
