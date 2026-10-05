package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * float 常量值：0.55f 除以 2 应输出为 0.55f 与 / 2.0f，而非整数位模式。
 */
class TestFloatValue : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFloatValueFixture.TestCls::class.java))
			.code()
			.doesNotContain("1073741824")
			.containsOne("0.55f")
			.containsOne("fa[0] = fa[0] / 2.0f;")
	}
}
