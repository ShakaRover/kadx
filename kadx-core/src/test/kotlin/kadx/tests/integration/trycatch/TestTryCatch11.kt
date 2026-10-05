package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

// Ensure that we can still merge if conditions even in the case where
// the BOTTOM_SPLITTER occurs before the final IF block.
/**
 * try-with-resources 与复合条件：即使 BOTTOM_SPLITTER 出现在最终 IF 块之前，也应合并条件。
 */
class TestTryCatch11 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		assertThat(getClassNode(TestTryCatch11Fixture.TestCls::class.java))
			.code()
			.containsOne("value.startsWith(\"content://\") || (!value.startsWith(\"/\") && !value.startsWith(\"file://\"))")
	}
}
