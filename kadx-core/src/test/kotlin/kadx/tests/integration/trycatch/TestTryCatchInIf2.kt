package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * if 内 try/catch 后继续使用外部变量（#2384）：仅需成功反编译，不额外断言。
 */
class TestTryCatchInIf2 : IntegrationTest() {

	@Test
	fun test() {
		// happens only without debug info and java version >= 10
		noDebugInfo()
		useTargetJavaVersion(10)
		KadxAssertions.assertThat(getClassNode(TestTryCatchInIf2Fixture.TestCls::class.java))
			.code()
	}
}
