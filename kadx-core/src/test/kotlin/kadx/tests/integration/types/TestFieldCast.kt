package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字段访问强转：访问父类私有/包私有字段时必要的 `(A)` 强转应保留，不应插入多余临时变量。
 *
 * Issue #962
 */
class TestFieldCast : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestFieldCastFixture.TestCls::class.java))
			.code()
			.containsOne("((A) this)")
			.containsOne("((A) b)")
			.containsOne("((A) t)")
			.doesNotContain("unused =")
			.doesNotContain("access modifiers changed")
	}
}
