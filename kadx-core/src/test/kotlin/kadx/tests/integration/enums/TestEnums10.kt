package kadx.tests.integration.enums

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举字段已被删除、但仍残留在 `values` 数组里：仍应还原为枚举类，
 * 并保留 4 个名为 `Fake field` 的占位字段。
 */
class TestEnums10 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("Failed to restore enum class")
			.containsOne("enum TestEnums10 {")
			.countString(4, "Fake field")
	}
}
