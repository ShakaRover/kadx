package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 1527：`Object[]` 强转后的数组取值应保留显式强转 `(Object[]) objectArray`。
 */
@SuppressWarnings("CommentedOutCode")
class TestTypeResolver21 : SmaliTest() {
	// @formatter:off
	/*
		public Number test(Object objectArray) {
			Object[] arr = (Object[]) objectArray;
			return (Number) arr[0];
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("Object[] arr = (Object[]) objectArray;")
	}
}
