package kadx.tests.integration.deobf

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 抽象方法重命名：抽象方法 `a()` 及其调用点都应被重命名。
 */
class TestMthRename : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		enableDeobfuscation()
		assertThat(getClassNode(TestMthRenameFixture.TestCls::class.java))
			.code()
			.doesNotContain("public abstract void a();")
			.doesNotContain(".a();")
	}
}
