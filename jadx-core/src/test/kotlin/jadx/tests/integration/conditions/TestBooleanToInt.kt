package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 int：三元表达式应生成 `1 : 0`。
 */
@Suppress("CommentedOutCode")
class TestBooleanToInt : SmaliTest() {

	// @formatter:off
	/*
		private boolean showConsent;

		public void write(int b) {
		}

		public void writeToParcel(TestBooleanToInt testBooleanToInt) {
			testBooleanToInt.write(this.showConsent ? 1 : 0);
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("write(this.showConsent ? 1 : 0);")
	}
}
