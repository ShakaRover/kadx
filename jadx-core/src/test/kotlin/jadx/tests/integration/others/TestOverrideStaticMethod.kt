package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
