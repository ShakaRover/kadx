package jadx.tests.integration.conditions

import jadx.NotYetImplemented
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件 18：`this == obj || (...)` 的短路形式当前被还原为提前返回；完整条件合并为已知未实现。
 */
@Suppress("CommentedOutCode")
class TestConditions18 : SmaliTest() {

	// @formatter:off
	/*
		public static class TestConditions18 {
			private Map map;

			public boolean test(Object obj) {
				return this == obj || ((obj instanceof TestConditions18) && st(this.map, ((TestConditions18) obj).map));
			}

			private static boolean st(Object obj, Object obj2) {
				return false;
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsLines(
				2,
				"if (this != obj) {",
				indent() + "return (obj instanceof TestConditions18) && st(this.map, ((TestConditions18) obj).map);",
				"}",
				"return true;",
			)
	}

	@Test
	@NotYetImplemented
	fun testNYI() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("return this == obj || ((obj instanceof TestConditions18) && st(this.map, ((TestConditions18) obj).map));")
	}
}
