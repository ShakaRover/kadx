package kadx.tests.integration.enums

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 带字段与接口实现的枚举：应还原 `TIMES("*")` 常量与 `DIVIDE("/")`。
 */
class TestEnums2a : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnums2aFixture.TestCls::class.java))
			.code()
			.containsOne("TIMES(\"*\") {")
			.containsOne("DIVIDE(\"/\")")
	}
}
