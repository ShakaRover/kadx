package jadx.tests.integration.trycatch

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 变量声明后紧跟 try（Issue #62），当前还原仍不完善（已知未实现）。
 */
class TestTryAfterDeclaration : IntegrationTest() {

	/**
	 * Issue #62.
	 */
	@Test
	@NotYetImplemented
	fun test62() {
		JadxAssertions.assertThat(getClassNode(TestTryAfterDeclarationFixture.TestClass::class.java))
			.code()
			.containsOne("try {")
	}
}
