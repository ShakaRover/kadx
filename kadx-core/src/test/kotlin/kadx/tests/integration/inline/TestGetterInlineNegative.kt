package kadx.tests.integration.inline

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * getter 内联的反例：把 `getter()` 内联成裸字段访问会生成 `field;`（非语句），
 * 因此这里必须保留 `return field;` 形式。
 */
class TestGetterInlineNegative : SmaliTest() {
	// @formatter:off
	/*
		public class TestGetterInlineNegative {
			public static final String field = "some string";

			public static synthetic String getter() {
				return field;
			}

			public void test() {
				getter(); // inline will produce 'field;' and fail to compile with 'not a statement' error
			}

			public String test2() {
				return getter();
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain(indent() + "field;")
			.containsOne("return field;")
	}
}
