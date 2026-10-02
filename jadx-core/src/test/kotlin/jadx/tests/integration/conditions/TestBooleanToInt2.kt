package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * boolean 转 int（装箱与拆箱）：`Integer.valueOf(v)` 与基本类型参数都应还原为 `value ? 1 : 0`。
 */
@Suppress("CommentedOutCode")
class TestBooleanToInt2 : SmaliTest() {

	// @formatter:off
	/*
		public static class TestCls {
			public void test() {
				boolean v = getValue();
				use1(Integer.valueOf(v));
				use2(v);
			}

			private boolean getValue() {
				return false;
			}

			private void use1(Integer v) {
			}

			private void use2(int v) {
			}
		}
	 */
	// @formatter:on
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("use1(Integer.valueOf(value ? 1 : 0));")
			.containsOne("use2(value ? 1 : 0);")
	}
}
