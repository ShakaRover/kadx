package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 double：三元表达式应生成 `1.0d : 0.0d` 字面量。
 */
class TestBooleanToDouble : SmaliTest() {

	// @formatter:off
	/*
		private boolean showConsent;

		public void write(double d) {
		}

		public void writeToParcel(TestBooleanToDouble testBooleanToDouble) {
			testBooleanToDouble.write(this.showConsent ? 1 : 0);
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("write(this.showConsent ? 1.0d : 0.0d);")
	}
}
