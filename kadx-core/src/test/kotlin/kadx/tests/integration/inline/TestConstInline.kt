package kadx.tests.integration.inline

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 常量内联：`f(0)` 调用不应被内联为常量，`catch` 分支应保留 `return false;`。
 */
class TestConstInline : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConstInlineFixture.TestCls::class.java))
			.code()
			.containsOne("return f(0);")
			.containsOne("return false;")
			.doesNotContain(" = ")
	}
}
