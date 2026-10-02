package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * iterator.hasNext() 驱动的 while 循环。
 */
class TestLoopDetection4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopDetection4Fixture.TestCls::class.java))
			.code()
			.containsOne("while (this.iterator.hasNext()) {")
			.containsOne("if (filtered != null) {")
			.containsOne("return filtered;")
			.containsOne("return null;")
	}
}
