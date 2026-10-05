package kadx.tests.integration.enums

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 大量枚举常量时，常量内联不应破坏 `$VALUES` 数组长度对应的常量（`E42` 等）。
 */
class TestEnumWithConstInlining : SmaliTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestEnumWithConstInliningFixture.TestCls::class.java))
			.code()
			.containsOne("E42,")
	}
}
