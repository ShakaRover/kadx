package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 921（第二种情况）：布尔取反参与的三元表达式，应还原出 `useInt(z ? 0 : 8)` 等形式。
 */
class TestTypeResolver15 : SmaliTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver15Fixture.TestCls::class.java))
			.code()
			// .containsOne("useInt(!z ? 1 : 0);") // TODO: convert to ternary
			.containsOne("useInt(z ? 0 : 8);")
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("useInt(z ? 0 : 8);")
			.containsOne("useInt(!z ? 1 : 0);")
	}
}
