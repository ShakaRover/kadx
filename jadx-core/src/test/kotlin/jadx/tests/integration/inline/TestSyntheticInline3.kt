package jadx.tests.integration.inline

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 合成访问器内联（smali）：匿名 `Function` 访问外部字段/方法时不应残留 `.access$...`。
 */
class TestSyntheticInline3 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSyntheticInline3Fixture.TestCls::class.java))
			.code()
	}

	@Test
	fun testSmali() {
		allowWarnInCode()
		disableCompilation()
		assertThat(getClassNodeFromSmaliFiles())
			.code()
			.doesNotContain(".access\$getDialog\$p(")
			.doesNotContain(".access\$getChooserIntent(")
			.doesNotContain("= r1;")
			.doesNotContain("this\$0")
	}
}
