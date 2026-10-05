package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型边界字段访问：`((TestCls) t).field` 不应生成 `t.field`。
 */
class TestFieldAccess : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldAccessFixture.TestCls::class.java))
			.code()
			.doesNotContain("t.field")
	}
}
