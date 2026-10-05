package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器中三元表达式只出现在一个分支：`this(...)` 中的三元参数应被保留。
 */
class TestTernaryOneBranchInConstructor2 : SmaliTest() {

	// @formatter:off
	/*
		public class A {
			public A(String str, String str2, String str3, boolean z) {}

			public A(String str, String str2, String str3, boolean z, int i, int i2) {
				this(str, (i & 2) != 0 ? "" : str2, (i & 4) != 0 ? "" : str3, (i & 8) != 0 ? false : z);
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("this(str, (i & 2) != 0 ? \"\" : str2, (i & 4) != 0 ? \"\" : str3, (i & 8) != 0 ? false : z);")
			.doesNotContain("//")
	}
}
