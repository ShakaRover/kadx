package kadx.tests.integration.inline

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 合成访问器内联：内部类 `A` 读写外部字段/调用外部方法时应直接访问，不残留 `access$`/`x0`。
 */
class TestSyntheticInline : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSyntheticInlineFixture.TestCls::class.java))
			.code()
			.doesNotContain("synthetic")
			.doesNotContain("access\$")
			.doesNotContain("x0")
			.contains("f = v;")
			.contains("return TestSyntheticInlineFixture\$TestCls.this.f;")
			// .contains("return f;");
			// .contains("return func();");
			// Temporary solution
			.contains("return TestSyntheticInlineFixture\$TestCls.this.func();")
	}
}
