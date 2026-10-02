package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造函数参数传递：`new SomeObject(arg3)` 应保留构造参数，且不出现多余的临时变量。
 */
class TestConstructor : SmaliTest() {
	// @formatter:off
	/*
		private SomeObject test(double r23, double r25, SomeObject r27) {
			SomeObject r17 = new SomeObject
			r0 = r17
			r1 = r27
			r0.<init>(r1)
			return r17
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("new SomeObject(arg3);")
			.doesNotContain("= someObject")
	}
}
