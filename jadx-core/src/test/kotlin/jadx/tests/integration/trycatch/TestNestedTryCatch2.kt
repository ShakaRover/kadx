package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 嵌套 try/catch：内外两层 catch 均应保留。
 */
class TestNestedTryCatch2 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA8, TestProfile.DX_J8)
	fun test() {
		assertThat(getClassNode(TestNestedTryCatch2Fixture.TestCls::class.java))
			.code()
			.countString(2, "} catch (Exception ")
	}
}
