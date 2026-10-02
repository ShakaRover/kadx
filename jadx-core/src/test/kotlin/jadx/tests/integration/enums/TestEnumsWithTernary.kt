package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 枚举常量参数使用三元表达式：应内联为 `useNumber() ? "1" : "2"`，且不生成静态块。
 */
class TestEnumsWithTernary : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J8)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestEnumsWithTernaryFixture.TestCls::class.java))
			.code()
			.containsOne("ANY(useNumber() ? \"1\" : \"2\");")
			.doesNotContain("static {")
	}
}
