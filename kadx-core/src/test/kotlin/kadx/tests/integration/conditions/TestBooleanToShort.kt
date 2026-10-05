package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 short：三元表达式两侧都应保留 `(short)` 强制转换。
 */
class TestBooleanToShort : SmaliTest() {

	// @formatter:off
	/*
		private boolean showConsent;

		public void write(short b) {
		}

		public void writeToParcel(TestBooleanToShort testBooleanToShort) {
			testBooleanToShort.write(this.showConsent ? (short) 1 : 0);
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("write(this.showConsent ? (short) 1 : (short) 0);")
	}
}
