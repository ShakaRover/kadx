package kadx.tests.integration.inner

import kadx.api.CommentsLevel
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类实例初始化块：`Something a = new Something() { { put(...) } };` 的还原形态。
 */
class TestAnonymousClass16 : IntegrationTest() {

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.NONE
		noDebugInfo()
		assertThat(getClassNode(TestAnonymousClass16Fixture.TestCls::class.java))
			.code()
			.doesNotContain("r0")
			.doesNotContain("AnonymousClass1 r0 = ")
			.containsLines(
				2,
				"Something something = new Something() {",
				indent() + "{",
				indent(2) + "put(\"a\", \"b\");",
				indent() + "}",
				"};",
			)
	}
}
