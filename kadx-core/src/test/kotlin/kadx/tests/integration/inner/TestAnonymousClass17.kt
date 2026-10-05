package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件表达式内联赋值：`if (a && (v = get(b)) != null)` 应原样还原。
 */
class TestAnonymousClass17 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestAnonymousClass17Fixture.TestCls::class.java))
			.code()
			.containsOne("if (a && (v = get(b)) != null) {")
	}
}
