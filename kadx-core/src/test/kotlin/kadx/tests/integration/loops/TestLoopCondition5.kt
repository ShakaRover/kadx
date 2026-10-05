package kadx.tests.integration.loops

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 反向遍历的索引循环：Java 输入与 smali 输入都应保留循环结构。
 */
class TestLoopCondition5 : SmaliTest() {

	@Test
	fun test0() {
		assertThat(getClassNode(TestLoopCondition5Fixture.TestCls::class.java))
			.code()
			.containsOne("for (")
			.containsOne("return -1;")
			.countString(2, "return ")
	}

	@Test
	fun test1() {
		assertThat(getClassNodeFromSmaliWithPath("loops", "TestLoopCondition5"))
			.code()
			.containsOneOf("for (", "while (true) {", "} while (iArr[i3] != i);")
			.containsOne("return -1;")
			.countString(2, "return ")
	}
}
