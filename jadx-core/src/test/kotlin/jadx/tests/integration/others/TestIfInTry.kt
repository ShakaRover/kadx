package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * try 中的 if：try 内提前 return 与后续 try/catch 的结构应正确还原。
 */
class TestIfInTry : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestIfInTryFixture.TestCls::class.java))
			.code()
			.containsOne("if (a != 0) {")
			.containsOne("} catch (Exception e) {")
			.countString(2, "try {")
			.countString(3, "f()")
			.containsOne("return 1;")
			.containsOne("} catch (IOException e")
			.containsOne("return -1;")
	}
}
