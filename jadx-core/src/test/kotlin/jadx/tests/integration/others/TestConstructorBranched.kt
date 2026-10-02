package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 分支化的 HashSet 构造：null 分支用无参构造，非 null 分支用集合构造。
 */
@Suppress("CommentedOutCode")
class TestConstructorBranched : SmaliTest() {
	// @formatter:off
	/*
		public Set<String> test(Collection<String> collection) {
			Set<String> set;
			if (collection == null) {
				set = new HashSet<>();
			} else {
				set = new HashSet<>(collection);
			}
			set.add("end");
			return set;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("new HashSet()")
			.containsOne("new HashSet(collection)")
	}
}
