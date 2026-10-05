package kadx.tests.integration.names

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 保留字/非法字段名：`do` 为 Java 保留字、`0f` 非法标识符，二者都应被重命名。
 */
class TestReservedNames : SmaliTest() {

	// @formatter:off
	/*
		public static class TestCls {

			public String do; // reserved name
			public String 0f; // invalid identifier

			public String try() {
				return this.do;
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("public String do;")
	}
}
