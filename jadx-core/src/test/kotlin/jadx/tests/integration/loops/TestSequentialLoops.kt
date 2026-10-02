package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 顺序排列的两个 while 循环（第二个条件中带赋值）。
 */
class TestSequentialLoops : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSequentialLoopsFixture.TestCls::class.java))
			.code()
			.countString(2, "while (")
			.containsOne("break;")
			.containsOne("return c;")
	}
}
