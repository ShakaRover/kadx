package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
