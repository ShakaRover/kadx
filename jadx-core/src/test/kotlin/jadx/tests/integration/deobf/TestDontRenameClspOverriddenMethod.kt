package jadx.tests.integration.deobf

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 混淆还原时不应重命名 `clsp`（类签名多态）覆写的方法：两个 `run()` 都应保留原方法名。
 */
class TestDontRenameClspOverriddenMethod : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything

		assertThat(getClassNode(TestDontRenameClspOverriddenMethodFixture.TestCls::class.java))
			.code()
			.countString(2, "public void run() {")
	}
}
