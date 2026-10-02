package jadx.tests.integration.enums

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举带字段及静态常量别名（`DEFAULT`/`MAX`）：反编译结果必须可编译且能通过 `check()`。
 */
class TestEnumWithFields : SmaliTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestEnumWithFieldsFixture.TestCls::class.java))
			.code()
	}

	@Test
	fun test2() {
		assertThat(getClassNodeFromSmali())
			.code()
	}
}
