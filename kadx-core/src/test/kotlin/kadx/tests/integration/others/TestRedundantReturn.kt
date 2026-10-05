package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 冗余 return：方法末尾的无返回值 `return;` 不应出现在输出中。
 */
class TestRedundantReturn : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestRedundantReturnFixture.TestCls::class.java))
			.code()
			.doesNotContain("return;")
	}
}
