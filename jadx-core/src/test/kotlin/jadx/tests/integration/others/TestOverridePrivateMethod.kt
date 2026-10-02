package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
