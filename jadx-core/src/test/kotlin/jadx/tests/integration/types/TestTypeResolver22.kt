package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * `InputStream.skip` 返回 `long`：循环变量 `pos` 应还原为 `long` 类型。
 */
class TestTypeResolver22 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA8, TestProfile.DX_J8)
	fun test() {
		assertThat(getClassNode(TestTypeResolver22Fixture.TestCls::class.java))
			.code()
			.containsOne("long pos = ")
	}
}
