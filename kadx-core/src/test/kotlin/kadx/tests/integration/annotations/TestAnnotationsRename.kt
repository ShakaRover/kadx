package kadx.tests.integration.annotations

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 注解重命名：开启混淆还原后，注解方法 `x()` 应被重命名，注解调用点不再显示 `(x = 5)`。
 */
class TestAnnotationsRename : IntegrationTest() {

	@Test
	fun test() {
		enableDeobfuscation()
		KadxAssertions.assertThat(getClassNode(TestAnnotationsRenameFixture.TestCls::class.java))
			.code()
			.contains("public @interface ")
			.doesNotContain("(x = 5)")
	}
}
