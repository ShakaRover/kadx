package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 三元表达式 4：循环内的三元赋值不应被还原为 try/catch。
 */
@Suppress("CommentedOutCode")
class TestTernary4 : SmaliTest() {

	// @formatter:off
	/*
		private Set test(HashMap<String, Object> hashMap) {
			boolean z;
			HashSet hashSet = new HashSet();
			synchronized (this.defaultValuesByPath) {
				for (String next : this.defaultValuesByPath.keySet()) {
					Object obj = hashMap.get(next);
					if (obj != null) {
						z = !getValueObject(next).equals(obj);
					} else {
						z = this.valuesByPath.get(next) != null;;
					}
					if (z) {
						hashSet.add(next);
					}
				}
			}
			return hashSet;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.removeBlockComments()
			.doesNotContain("5")
			.doesNotContain("try")
	}
}
