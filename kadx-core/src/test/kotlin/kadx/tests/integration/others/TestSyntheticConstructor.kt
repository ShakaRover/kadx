package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 合成构造器：静态块中调用构造函数，反编译第 2 行应为 `new BuggyConstructor();`。
 */
class TestSyntheticConstructor : SmaliTest() {
	// @formatter:off
	/*
		public class Test {
			static {
				new BuggyConstructor();
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmaliFiles("Test"))
			.code()
			.containsLine(2, "new BuggyConstructor();")
	}
}
