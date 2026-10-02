package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 重载方法 `String.replace(char, char)`：无调试信息时字符字面量应正确还原。
 */
class TestCastInOverloadedInvoke4 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestCastInOverloadedInvoke4Fixture.TestCls::class.java))
			.code()
			.containsOne("return str.replace('\\n', ' ');")
	}
}
