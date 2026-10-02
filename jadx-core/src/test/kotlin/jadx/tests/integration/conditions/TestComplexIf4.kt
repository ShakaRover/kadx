package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 复杂条件 4：恒真条件 `0 >= 0` 应被保留（不折叠）。
 */
class TestComplexIf4 : SmaliTest() {
	@Test
	fun test() {
		disableCompilation()
		allowWarnInCode() // this is just to allow a harmless duplicated region warning
		assertThat(getClassNodeFromSmali()).code().contains("if (0 >= 0) {")
	}
}
