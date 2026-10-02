package jadx.tests.integration.inner

import jadx.api.CommentsLevel
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
