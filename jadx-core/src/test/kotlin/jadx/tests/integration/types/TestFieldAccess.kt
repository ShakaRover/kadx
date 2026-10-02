package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
