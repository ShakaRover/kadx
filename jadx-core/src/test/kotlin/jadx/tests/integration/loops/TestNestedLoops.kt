package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 两层嵌套 for-each 循环的还原。
 */
class TestNestedLoops : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestNestedLoopsFixture.TestCls::class.java))
			.code()
			.containsOne("for (String s1 : l1) {")
			.containsOne("for (String s2 : l2) {")
			.containsOne("if (s1.equals(s2)) {")
			.containsOne("l2.add(s1);")
			.containsOne("l1.remove(s2);")
	}
}
