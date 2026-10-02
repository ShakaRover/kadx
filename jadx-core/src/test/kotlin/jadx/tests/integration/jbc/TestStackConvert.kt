package jadx.tests.integration.jbc

import jadx.tests.api.RaungTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * JBC 栈转换：try/catch 中 `Integer.parseInt(num)` 调用应完整保留（Java 与 raung 输入）。
 */
class TestStackConvert : RaungTest() {

	@TestWithProfiles(TestProfile.JAVA11)
	fun test() {
		assertThat(getClassNode(TestStackConvertFixture.TestCls::class.java))
			.code()
			.containsOne("Integer.parseInt(num)")
	}

	@Test
	fun testRaung() {
		assertThat(getClassNodeFromRaung())
			.code()
			.containsOne("Integer.parseInt(num)")
	}
}
