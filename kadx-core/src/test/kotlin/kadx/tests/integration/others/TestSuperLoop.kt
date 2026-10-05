package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 父类循环继承：A extends B、B extends A 的非法继承关系不应导致死循环。
 */
@Suppress("CommentedOutCode")
class TestSuperLoop : SmaliTest() {
	// @formatter:off
	/*
		public class A extends B {
			public int a;
		}

		public class B extends A {
			public int b;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		allowWarnInCode()
		disableCompilation()

		val clsList = loadFromSmaliFiles()
		assertThat(searchCls(clsList, "A"))
			.code()
			.containsOne("public class A extends B {")

		assertThat(searchCls(clsList, "B"))
			.code()
			.containsOne("public class B extends A {")
	}
}
