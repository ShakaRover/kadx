package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 无参枚举构造器应委托给带参构造器（`this(0)`），反编译后保留 `Numbers()` 与 `ONE(1);`。
 */
class TestEnums6 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnums6Fixture.TestCls::class.java))
			.code()
			.containsOne("ZERO,")
			.containsOne("Numbers() {")
			.containsOne("ONE(1);")
	}
}
