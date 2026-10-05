package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
