package kadx.tests.integration.inner

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 用例：伪造的合成构造器应被正确识别并还原。
 */
class TestInnerClassFakeSyntheticConstructor : SmaliTest() {

	// @formatter:off
	/*
		public class TestCls {
			public synthetic TestCls(String a) {
				this(a, true);
			}

			public TestCls(String a, boolean b) {
			}

			public static TestCls build(String str) {
				return new TestCls(str);
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali("inner/TestInnerClassFakeSyntheticConstructor", "kadx.tests.inner.TestCls"))
			.code()
			.containsOne("TestCls(String a) {")
		// and must compile
	}
}
