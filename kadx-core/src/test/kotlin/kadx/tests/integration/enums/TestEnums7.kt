package kadx.tests.integration.enums

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举构造器中使用 `name().equals(...)` 判断：应还原无参构造器与常量声明。
 */
class TestEnums7 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnums7Fixture.TestCls::class.java))
			.code()
			.containsOne("ZERO,")
			.containsOne("ONE;")
			.containsOne("Numbers() {")
	}
}
