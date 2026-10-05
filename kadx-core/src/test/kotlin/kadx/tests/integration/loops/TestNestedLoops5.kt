package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套循环中不应产生多余的 continue。
 */
class TestNestedLoops5 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestNestedLoops5Fixture.TestCls::class.java))
			.code()
			.doesNotContain("continue;")
	}
}
