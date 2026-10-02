package jadx.tests.integration.invoke

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 自定义 invoke-custom：等价的多态调用应能正确还原。
 */
class TestRawCustomInvoke : SmaliTest() {

	@Test
	fun test() {
		noDebugInfo()
		// this code does not contain `invoke-custom` instruction
		// only check if equivalent polymorphic call is correct
		assertThat(getClassNode(TestRawCustomInvokeFixture.TestCls::class.java))
			.code()
			.containsOne(
				"return (String) staticBootstrap(MethodHandles.lookup(), \"func\", MethodType.methodType(String.class, Integer.TYPE, Double.TYPE)).dynamicInvoker().invoke(1, 2.0d);",
			)
	}

	@Test
	fun testSmali() {
		forceDecompiledCheck()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne(
				"return (String) staticBootstrap(MethodHandles.lookup(), \"func\", MethodType.methodType(String.class, Integer.TYPE, Double.TYPE)).dynamicInvoker().invoke(1, 2.0d) /* invoke-custom */;",
			)
	}
}
