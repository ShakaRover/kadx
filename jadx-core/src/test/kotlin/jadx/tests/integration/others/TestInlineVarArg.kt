package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 可变参数内联：可变参数调用应内联为 f("a", "b", "c")。
 */
class TestInlineVarArg : SmaliTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("f(\"a\", \"b\", \"c\");")
	}
}
