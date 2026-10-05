package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 byte：三元表达式两侧都应保留 `(byte)` 强制转换。
 */
class TestBooleanToByte : SmaliTest() {

	// @formatter:off
	/*
		private boolean showConsent;

		public void write(byte b) {
		}

		public void writeToParcel(TestBooleanToByte testBooleanToByte) {
			testBooleanToByte.write(this.showConsent ? (byte) 1 : 0);
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("write(this.showConsent ? (byte) 1 : (byte) 0);")
	}
}
