package kadx.tests.integration.enums

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套枚举（枚举内再定义枚举）作为构造参数：应还原两个嵌套枚举及其常量。
 */
class TestInnerEnums : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInnerEnumsFixture.TestCls::class.java))
			.code()
			.containsOne("ONE((byte) 1, NumString.ONE)")
			.containsOne("ONE(\"one\")")
	}
}
