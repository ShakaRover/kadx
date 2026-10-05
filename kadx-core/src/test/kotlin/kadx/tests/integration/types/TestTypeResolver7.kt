package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import kadx.tests.api.utils.assertj.KadxCodeAssertions
import org.junit.jupiter.api.Test

/**
 * 三元表达式分支上的强转：`use(b ? (Exception) getObj() : null)` 的两种等价形态均应被接受。
 */
class TestTypeResolver7 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver7Fixture.TestCls::class.java))
			.code()
			.oneOf(
				{ c: KadxCodeAssertions -> c.containsOne("use(b ? (Exception) getObj() : null);") },
				{ c: KadxCodeAssertions -> c.containsOne("use(b ? (Exception) getObj() : (Exception) null);") },
			)
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestTypeResolver7Fixture.TestCls::class.java)
	}
}
