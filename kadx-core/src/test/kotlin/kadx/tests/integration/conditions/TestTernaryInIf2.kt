package kadx.tests.integration.conditions

import kadx.NotYetImplemented
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * if 中的三元表达式 2：嵌套的 null 安全 equals 应还原为三元条件。
 */
class TestTernaryInIf2 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTernaryInIf2Fixture.TestCls::class.java))
			.code()
			.containsLines(2, "if (this.a != null ? this.a.equals(other.a) : other.a == null) {")
		// .containsLines(3, "if (this.b != null ? this.b.equals(other.b) : other.b == null) {")
		// .containsLines(4, "return true;")
		// .containsLines(2, "return false;")
	}

	@Test
	@NotYetImplemented
	fun testNYI() {
		assertThat(getClassNode(TestTernaryInIf2Fixture.TestCls::class.java))
			.code()
			.containsLines(
				2,
				"return (this.a != null ? this.a.equals(other.a) : other.a == null) " +
					"&& (this.b == null ? other.b == null : this.b.equals(other.b));",
			)
	}

	@Test
	fun test2() {
		getClassNodeFromSmaliWithPath("conditions", "TestTernaryInIf2")
	}
}
