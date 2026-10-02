package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套枚举（枚举内再定义枚举）作为构造参数：应还原两个嵌套枚举及其常量。
 */
class TestInnerEnums : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInnerEnumsFixture.TestCls::class.java))
			.code()
			.containsOne("ONE((byte) 1, NumString.ONE)")
			.containsOne("ONE(\"one\")")
	}
}
