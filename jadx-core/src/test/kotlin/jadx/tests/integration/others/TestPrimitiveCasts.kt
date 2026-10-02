package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 原始类型强制转换：`int`/`long`/`char` 之间的显式窄化转换应正确输出，
 * 不能出现多余的 `(0)` 或嵌套转换。
 */
class TestPrimitiveCasts : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestPrimitiveCastsFixture.TestCls::class.java))
			.code()
			.doesNotContain("(0)")
			.doesNotContain(") ((int) getLong())")
	}
}
