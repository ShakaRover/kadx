package jadx.tests.integration.deobf

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 继承方法重命名：对 `A.call()` 的引用应解析到父类 `B.call()` 并使用其别名。
 */
class TestInheritedMethodRename : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		enableDeobfuscation()
		getArgs().deobfuscationMinLength = 99

		assertThat(getClassNode(TestInheritedMethodRenameFixture.TestCls::class.java))
			.code()
			.containsOne("public void m1call() {")
			.doesNotContain(".call();")
			.containsOne(".m1call();")
	}
}
