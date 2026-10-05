package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 私有方法覆写：私有方法不构成覆写，不应添加 `@Override`。
 */
class TestOverridePrivateMethod : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestOverridePrivateMethodFixture.TestCls::class.java))
			.code()
			.doesNotContain("@Override")
	}
}
