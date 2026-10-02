package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 非法异常声明（二）：未知类型层次结构时仍应保留 throws 声明。
 */
class TestInvalidExceptions2 : SmaliTest() {

	@Test
	fun test() {
		allowWarnInCode()
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("throwPossibleExceptionType() throws UnknownTypeHierarchyException {")
	}
}
