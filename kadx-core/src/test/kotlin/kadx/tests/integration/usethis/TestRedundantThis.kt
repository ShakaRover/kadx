package kadx.tests.integration.usethis

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * 冗余的 `this` 前缀应被去掉，但字段赋值场景需保留 `this` 以区分同名参数。
 */
class TestRedundantThis : IntegrationTest() {

	// @Test
	fun test() {
		assertThat(getClassNode(TestRedundantThisFixture.TestCls::class.java))
			.code()
			.doesNotContain("this.f1();")
			.doesNotContain("return this.field1;")
			.contains("this.field2 = field2;")
	}
}
