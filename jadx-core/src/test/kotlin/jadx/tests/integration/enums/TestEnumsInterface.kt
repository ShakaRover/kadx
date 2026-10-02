package jadx.tests.integration.enums

import jadx.api.CommentsLevel
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 实现接口的枚举：应保留 `implements IOperation` 以及各常量对接口方法的实现。
 */
class TestEnumsInterface : IntegrationTest() {

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.WARN
		assertThat(getClassNode(TestEnumsInterfaceFixture.TestCls::class.java))
			.code()
			.containsLines(
				1,
				"public enum Operation implements IOperation {",
				indent(1) + "PLUS {",
				indent(2) + "@Override",
				indent(2) + "public int apply(int x, int y) {",
				indent(3) + "return x + y;",
				indent(2) + '}',
				indent(1) + "},",
				indent(1) + "MINUS {",
				indent(2) + "@Override",
				indent(2) + "public int apply(int x, int y) {",
				indent(3) + "return x - y;",
				indent(2) + '}',
				indent(1) + '}',
				"}",
			)
	}
}
