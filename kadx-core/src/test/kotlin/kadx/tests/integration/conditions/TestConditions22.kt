package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件 22：else-if 链的缩进与结构；Java 源码输入与 smali 输入分别校验。
 */
class TestConditions22 : SmaliTest() {

	@TestWithProfiles(TestProfile.JAVA17)
	fun testJava() {
		noDebugInfo()
		assertThat(getClassNode(TestConditions22Fixture.TestCls::class.java))
			.code()
			.containsOne(indent(2) + "if (")
			.containsOne(indent(2) + "} else if (")
			.containsOne(indent(2) + "} else {")
	}

	@Test
	fun testSmali() {
		allowWarnInCode() // TODO: don't add 'duplicated region' warning for small and/or constant code
		forceDecompiledCheck()
		assertThat(getClassNodeFromSmali())
			.code()
	}
}
