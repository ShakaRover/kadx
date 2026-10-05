package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 float：三元表达式应生成 `1.0f : 0.0f` 字面量。
 */
class TestBooleanToFloat : SmaliTest() {

	// @formatter:off
	/*
		private boolean showConsent;

		public void write(float f) {
		}

		public void writeToParcel(TestBooleanToFloat testBooleanToFloat) {
			testBooleanToFloat.write(this.showConsent ? 1 : 0);
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("write(this.showConsent ? 1.0f : 0.0f);")
	}
}
