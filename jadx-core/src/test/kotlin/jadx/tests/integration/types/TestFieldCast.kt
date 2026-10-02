package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
