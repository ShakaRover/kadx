package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 两个枚举的 switch：应只生成一份重映射数组，且分别还原两组 `case`。
 */
class TestSwitchOverEnum2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchOverEnum2Fixture::class.java))
			.code()
			.doesNotContain("synthetic")
			.countString(1, "switch (c) {")
			.countString(1, "case ONE:")
			.countString(1, "case DOG:")
	}
}
