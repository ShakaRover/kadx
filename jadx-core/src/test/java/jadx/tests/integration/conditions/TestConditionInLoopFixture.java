package jadx.tests.integration.conditions;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestConditionInLoopFixture {

	public static class TestCls {
		private static int test(int a, int b) {
			int c = a + b;
			for (int i = a; i < b; i++) {
				if (i == 7) {
					c += 2;
				} else {
					c *= 2;
				}
			}
			c--;
			return c;
		}

		public void check() {
			assertThat(test(5, 9)).isEqualTo(115);
			assertThat(test(8, 23)).isEqualTo(1015807);
		}
	}
}
