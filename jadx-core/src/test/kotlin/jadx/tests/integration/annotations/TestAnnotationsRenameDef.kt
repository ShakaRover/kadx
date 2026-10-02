package jadx.tests.integration.annotations

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 注解重命名定义：开启混淆还原后，注解方法 `value()` 应被重命名，调用点参数也随之变化。
 */
class TestAnnotationsRenameDef : IntegrationTest() {

	@Test
	fun test() {
		enableDeobfuscation()
		// force rename 'value' method
		args.deobfuscationMinLength = 20

		JadxAssertions.assertThat(getClassNode(TestAnnotationsRenameDefFixture.TestCls::class.java))
			.code()
			.contains("public @interface ")
			.doesNotContain("int value();")
			.doesNotContain("(5)")
	}
}
