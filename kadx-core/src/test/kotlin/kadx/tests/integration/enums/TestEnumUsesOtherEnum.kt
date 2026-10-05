package kadx.tests.integration.enums

import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举构造器引用同枚举其它常量：应还原 `OTHER_INT(INT);`，且不生成静态块。
 */
class TestEnumUsesOtherEnum : SmaliTest() {

	@TestWithProfiles(TestProfile.D8_J11)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestEnumUsesOtherEnumFixture.TestCls::class.java))
			.code()
			.containsOne("OTHER_INT(INT);")
			.doesNotContain("\n        \n") // no indentation for empty string
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("public enum TestEnumUsesOtherEnum {")
			.doesNotContain("static {")
	}
}
