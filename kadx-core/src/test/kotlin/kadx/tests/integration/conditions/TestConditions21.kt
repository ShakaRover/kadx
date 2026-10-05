package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件 21：instanceof 之后的集合判空与包含判断应合并为一个条件。
 */
class TestConditions21 : SmaliTest() {

	// @formatter:off
	/*
		public boolean check(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj instanceof List) {
				List list = (List) obj;
				if (!list.isEmpty() && list.contains(this)) {
					return true;
				}
			}
			return false;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali()).code()
			.containsOne("!list.isEmpty() && list.contains(this)")
	}
}
