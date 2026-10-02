package jadx.tests.integration.inline

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 常量内联：`f(0)` 调用不应被内联为常量，`catch` 分支应保留 `return false;`。
 */
class TestConstInline : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConstInlineFixture.TestCls::class.java))
			.code()
			.containsOne("return f(0);")
			.containsOne("return false;")
			.doesNotContain(" = ")
	}
}
