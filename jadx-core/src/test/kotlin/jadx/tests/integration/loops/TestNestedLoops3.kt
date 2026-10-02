package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带标签 break 与内层 try-catch 的嵌套循环。
 */
class TestNestedLoops3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestNestedLoops3Fixture.TestCls::class.java))
			.code()
			.containsOne("} catch (Exception e) {")
	}
}
