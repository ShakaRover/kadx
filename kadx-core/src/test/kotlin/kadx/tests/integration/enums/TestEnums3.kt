package kadx.tests.integration.enums

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 枚举常量参数引用外层静态字段 `three`：应还原 `ONE(1)` 与枚举构造器。
 */
class TestEnums3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestEnums3Fixture.TestCls::class.java))
			.code()
			.containsOne("ONE(1)")
			.containsOne("Numbers(int n) {")
		// assertThat(code, containsOne("THREE(three)"));
		// assertThat(code, containsOne("assertTrue(Numbers.ONE.getNum() == 1);"));
	}
}
