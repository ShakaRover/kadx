package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
