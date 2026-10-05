package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证拆箱（unboxing）还原：包装类型为 null 时赋默认值，返回时自动拆箱为基础类型。
 */
class TestDeboxing2 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestDeboxing2Fixture.TestCls::class.java))
			.code()
			.containsOne("long test(Long l)")
			.containsOne("if (l == null) {")
			.containsOne("l = 0L;")
			.containsOne("test(null)")
			.containsOne("test(0L)")
			// checks for 'check' method
			.countString(2, "isEqualTo(0L)")
			.containsOne("test(7L)")
			.containsOne("isEqualTo(7L)")
	}
}
