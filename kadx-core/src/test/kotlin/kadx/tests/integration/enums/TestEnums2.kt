package kadx.tests.integration.enums

import kadx.api.CommentsLevel
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 带抽象方法、且每个常量各自实现的枚举：应完整还原 `PLUS`/`MINUS` 匿名子类体。
 */
class TestEnums2 : IntegrationTest() {

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.WARN
		assertThat(getClassNode(TestEnums2Fixture.TestCls::class.java))
			.code()
			.containsLines(
				1,
				"public enum Operation {",
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
				indent(1) + "};",
				"",
				indent(1) + "public abstract int apply(int i, int i2);",
				"}",
			)
	}
}
