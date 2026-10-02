package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 常量内联：`null` 赋值不应被内联成 `0`，应分别还原为 `list = null;` 与 `str = null;`。
 */
class TestConstInline : SmaliTest() {
	// @formatter:off
	/*
		private static String test(boolean b) {
			List<String> list;
			String str;
			if (b) {
				list = Collections.emptyList();
				str = "1";
			} else {
				list = null;
				str = list; // not correct assign in java but bytecode allow it
			}
			return use(list, str);
		}

		private static String use(List<String> list, String str) {
			return list + str;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithPkg("types", "TestConstInline"))
			.code()
			.containsOne("list = null;")
			.containsOne("str = null;")
	}
}
