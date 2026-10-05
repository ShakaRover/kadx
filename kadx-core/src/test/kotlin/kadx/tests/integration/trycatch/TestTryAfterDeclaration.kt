package kadx.tests.integration.trycatch

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
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
		KadxAssertions.assertThat(getClassNode(TestTryAfterDeclarationFixture.TestClass::class.java))
			.code()
			.containsOne("try {")
	}
}
