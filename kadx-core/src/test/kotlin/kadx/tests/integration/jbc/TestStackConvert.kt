package kadx.tests.integration.jbc

import kadx.tests.api.RaungTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
