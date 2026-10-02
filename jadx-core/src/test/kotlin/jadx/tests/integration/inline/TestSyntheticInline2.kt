package jadx.tests.integration.inline

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 合成访问器内联：内部类访问外部类的 `call`/`super.call` 应直接调用，不残留 `access$`。
 */
class TestSyntheticInline2 : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation() // strange java compiler bug
		assertThat(getClassNode(TestSyntheticInline2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("synthetic")
			.doesNotContain("access\$").contains("TestSyntheticInline2Fixture\$TestCls.this.call();")
			.contains("TestSyntheticInline2Fixture\$TestCls.super.call();")
	}

	@Test
	fun testTopClass() {
		JadxAssertions.assertThat(getClassNode(TestSyntheticInline2Fixture::class.java))
			.code()
			.contains(indent(1) + "TestCls.super.call();")
	}
}
