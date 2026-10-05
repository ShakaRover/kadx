package kadx.tests.integration.enums

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举中的静态字段应被正确处理：只保留真实使用的 `INSTANCE` 与 `sB`，
 * 丢弃未使用的 `sA`/`sC`，并且不生成多余构造器。
 */
class TestEnumsWithStaticFields : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOnlyOnce("INSTANCE;")
			.containsOnlyOnce("private static c sB")
			.doesNotContain(" sA")
			.doesNotContain(" sC")
			.doesNotContain("private TestEnumsWithStaticFields(String str) {")
	}
}
