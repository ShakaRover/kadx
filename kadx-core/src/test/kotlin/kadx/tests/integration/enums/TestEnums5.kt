package kadx.tests.integration.enums

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Kotlin 生成的 `kotlin.collections.State` 枚举：应还原出四个枚举常量。
 */
class TestEnums5 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithClsName("kotlin.collections.State"))
			.code()
			.containsLines(
				"enum State {",
				indent() + "Ready,",
				indent() + "NotReady,",
				indent() + "Done,",
				indent() + "Failed",
				"}",
			)
	}
}
