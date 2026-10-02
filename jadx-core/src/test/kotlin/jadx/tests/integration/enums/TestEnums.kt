package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 多种枚举形态：空枚举、只有 `;` 的空枚举、普通枚举与单例枚举。
 */
class TestEnums : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnumsFixture.TestCls::class.java))
			.code()
			.containsLines(1, "public enum EmptyEnum {", "}")
			.containsLines(
				1,
				"public enum EmptyEnum2 {",
				indent(1) + ';',
				"",
				indent(1) + "public static void mth() {",
				indent(1) + '}',
				"}",
			)
			.containsLines(
				1,
				"public enum Direction {",
				indent(1) + "NORTH,",
				indent(1) + "SOUTH,",
				indent(1) + "EAST,",
				indent(1) + "WEST",
				"}",
			)
			.containsLines(
				1,
				"public enum Singleton {",
				indent(1) + "INSTANCE;",
				"",
				indent(1) + "public String test() {",
				indent(2) + "return \"\";",
				indent(1) + '}',
				"}",
			)
	}
}
