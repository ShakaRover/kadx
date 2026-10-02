package jadx.tests.integration.annotations

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 注解重命名：开启混淆还原后，注解方法 `x()` 应被重命名，注解调用点不再显示 `(x = 5)`。
 */
class TestAnnotationsRename : IntegrationTest() {

	@Test
	fun test() {
		enableDeobfuscation()
		JadxAssertions.assertThat(getClassNode(TestAnnotationsRenameFixture.TestCls::class.java))
			.code()
			.contains("public @interface ")
			.doesNotContain("(x = 5)")
	}
}
