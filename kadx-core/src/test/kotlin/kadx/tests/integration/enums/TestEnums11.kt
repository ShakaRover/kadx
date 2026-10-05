package kadx.tests.integration.enums

import kadx.tests.api.RaungTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Raung 输入的枚举：应还原为单常量枚举，并保留常量字段 `a = -99`。
 * 关闭 `EnumVisitor` 后，构造器不应被错误地删除。
 */
class TestEnums11 : RaungTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromRaung())
			.code()
			.containsLines("public enum TestEnums11 {", indent(1) + "UNKNOWN;")
			.containsOne("public final int a = -99;")
			.doesNotContain("TestEnums11() {")
	}

	@Test
	fun testDisableEnumRestore() {
		// constructor method incorrectly removed
		getArgs().disabledPasses.add("EnumVisitor")
		disableCompilation()
		assertThat(getClassNodeFromRaung())
			.code()
			.containsOne("public TestEnums11() {")
	}
}
