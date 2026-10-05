package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 两层嵌套 for-each 循环的还原。
 */
class TestNestedLoops : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestNestedLoopsFixture.TestCls::class.java))
			.code()
			.containsOne("for (String s1 : l1) {")
			.containsOne("for (String s2 : l2) {")
			.containsOne("if (s1.equals(s2)) {")
			.containsOne("l2.add(s1);")
			.containsOne("l1.remove(s2);")
	}
}
