package kadx.tests.integration.enums

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 输入中的枚举：反编译后应还原成 `enum TestEnums8` 声明。
 */
class TestEnums8 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("enum TestEnums8")
	}
}
