package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
