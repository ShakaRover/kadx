package jadx.tests.integration.enums

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
