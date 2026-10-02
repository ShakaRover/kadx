package jadx.tests.integration.enums

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 大量枚举常量时，常量内联不应破坏 `$VALUES` 数组长度对应的常量（`E42` 等）。
 */
class TestEnumWithConstInlining : SmaliTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestEnumWithConstInliningFixture.TestCls::class.java))
			.code()
			.containsOne("E42,")
	}
}
