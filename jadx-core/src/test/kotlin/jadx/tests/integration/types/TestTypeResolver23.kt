package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

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
