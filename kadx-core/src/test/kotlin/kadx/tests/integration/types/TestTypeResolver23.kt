package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * 多分支赋值的局部变量应保留 `long` 类型：`long v`。
 */
class TestTypeResolver23 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA8, TestProfile.DX_J8)
	fun test() {
		assertThat(getClassNode(TestTypeResolver23Fixture.TestCls::class.java))
			.code()
			.containsOne("long v")
	}
}
