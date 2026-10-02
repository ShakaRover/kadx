package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 方法参数在不同分支被赋为不同类型时的类型推断：应推断为 `Object`，且不生成 `Object obj2 = 0;`。
 */
class TestTypeResolver10 : SmaliTest() {

	/*
	 * Method argument assigned with different types in separate branches
	 */

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code().containsOne("Object test(String str, String str2)")
			.doesNotContain("Object obj2 = 0;")
	}
}
