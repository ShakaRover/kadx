package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
