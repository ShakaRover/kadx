package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带标签 break 与内层 try-catch 的嵌套循环。
 */
class TestNestedLoops3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestNestedLoops3Fixture.TestCls::class.java))
			.code()
			.containsOne("} catch (Exception e) {")
	}
}
