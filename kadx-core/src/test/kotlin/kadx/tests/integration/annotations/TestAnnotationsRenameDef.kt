package kadx.tests.integration.annotations

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
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

		KadxAssertions.assertThat(getClassNode(TestAnnotationsRenameDefFixture.TestCls::class.java))
			.code()
			.contains("public @interface ")
			.doesNotContain("int value();")
			.doesNotContain("(5)")
	}
}
