package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * if 与 switch 嵌套：条件内的 switch 应被保留，并产生 else 分支。
 */
class TestIfAndSwitch : SmaliTest() {

	// @formatter:off
	/*
		private final static int C = 0;

		private static int i;
		private static final Random rd = new Random();
		private static final int ACTION_MOVE = 2;

		public static boolean ifAndSwitch() {
			boolean update = false;
			if (rd.nextInt() == ACTION_MOVE) {
				switch (i) {
					case C:
						update = true;
						break;
				}
			}
			if (update) {
				return true;
			}
			return false;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		allowWarnInCode()
		KadxAssertions.assertThat(getClassNodeFromSmali())
			.code()
			.countString(1, "if (rd.nextInt() == ACTION_MOVE) {")
			.countString(1, "switch (")
			.countString(1, "else {")
	}
}
