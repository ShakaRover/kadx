package jadx.tests.integration.enums

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举方法中的 `assert` 语句：默认能还原 `ONE(1)`；处理 java assert 的能力尚未实现。
 */
class TestEnumsWithAssert : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnumsWithAssertFixture.TestCls::class.java)).code()
			.containsOne("ONE(1)")
			.doesNotContain("Failed to restore enum class")
	}

	@NotYetImplemented("handle java assert")
	@Test
	fun testNYI() {
		assertThat(getClassNode(TestEnumsWithAssertFixture.TestCls::class.java)).code()
			.containsOne("assert num > 0;")
			.doesNotContain("\$assertionsDisabled")
			.doesNotContain("throw new AssertionError()")
	}
}
