package kadx.tests.integration.types

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型 Map 的 for-each 与取值：`Map.Entry` 的 key/value 不应插入多余的 `Integer`/`String` 强转。
 */
class TestGenerics2 : SmaliTest() {

	// @formatter:off
	/*
		public void test() {
			Map<Integer, String> map = this.field;
			useInt(map.size());
			for (Map.Entry<Integer, String> entry : map.entrySet()) {
				useInt(entry.getKey().intValue());
				entry.getValue().trim();
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("for (Map.Entry<Integer, String> entry : map.entrySet()) {")
			.containsOne("useInt(entry.getKey().intValue());") // no Integer cast
			.containsOne("entry.getValue().trim();") // no String cast
	}
}
