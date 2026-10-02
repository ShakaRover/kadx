package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
