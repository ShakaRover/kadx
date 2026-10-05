package kadx.tests.integration.deobf

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 覆写方法重命名：接口方法 `m()` 及所有覆写实现应共享别名 `mo0m`。
 */
class TestRenameOverriddenMethod : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything

		assertThat(getClassNode(TestRenameOverriddenMethodFixture.TestCls::class.java))
			.code()
			.countString(2, "@Override")
			.countString(3, "renamed from: m")
			.containsOne("void mo0m();")
			.countString(2, "public void mo0m() {")
	}
}
