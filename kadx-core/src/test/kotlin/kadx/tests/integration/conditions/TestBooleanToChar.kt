package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 char：三元表达式两侧都应保留 `(char)` 强制转换。
 */
class TestBooleanToChar : SmaliTest() {

	// @formatter:off
	/*
		private boolean showConsent;

		public void write(char b) {
		}

		public void writeToParcel(TestBooleanToChar testBooleanToChar) {
			testBooleanToChar.write(this.showConsent ? (char) 1 : 0);
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("write(this.showConsent ? (char) 1 : (char) 0);")
	}
}
