package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 long：三元表达式应生成 `1L : 0L` 字面量。
 */
class TestBooleanToLong : SmaliTest() {

	// @formatter:off
	/*
		private boolean showConsent;

		public void write(long j) {
		}

		public void writeToParcel(TestBooleanToLong testBooleanToLong) {
			testBooleanToLong.write(this.showConsent ? 1 : 0);
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("write(this.showConsent ? 1L : 0L);")
	}
}
