package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * try 块内的循环：try/catch 与 while 循环结构应正确还原。
 */
class TestLoopInTry : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopInTryFixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("if (b) {")
			.containsOne("throw new Exception();")
			.containsOne("while (f()) {")
			.containsOne("s();")
			.containsOne("} catch (Exception e) {")
			.containsOne("return 1;")
			.containsOne("return 0;")
	}
}
