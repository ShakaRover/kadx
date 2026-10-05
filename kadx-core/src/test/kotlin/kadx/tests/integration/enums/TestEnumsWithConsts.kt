package kadx.tests.integration.enums

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举内部引用外层常量：应还原出 `Direction` 枚举的四个常量。
 */
class TestEnumsWithConsts : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnumsWithConstsFixture.TestCls::class.java))
			.code()
			.containsLines(
				1,
				"public enum Direction {",
				indent(1) + "NORTH,",
				indent(1) + "SOUTH,",
				indent(1) + "EAST,",
				indent(1) + "WEST",
				"}",
			)
	}
}
