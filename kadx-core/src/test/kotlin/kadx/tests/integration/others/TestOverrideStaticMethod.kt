package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 静态方法“覆写”：静态方法不构成覆写，不应添加 `@Override`。
 */
class TestOverrideStaticMethod : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestOverrideStaticMethodFixture.TestCls::class.java))
			.code()
			.doesNotContain("@Override")
	}
}
