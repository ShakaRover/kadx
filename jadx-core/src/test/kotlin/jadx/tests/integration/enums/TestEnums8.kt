package jadx.tests.integration.enums

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
