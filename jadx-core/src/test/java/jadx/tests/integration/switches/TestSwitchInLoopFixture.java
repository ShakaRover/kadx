package jadx.tests.integration.switches;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSwitchInLoopFixture {

	public static class TestCls {
		public int test(int k) {
			int a = 0;
			while (true) {
				switch (k) {
					case 0:
						return a;
					default:
						a++;
						k >>= 1;
				}
			}
		}

		public void check() {
			assertThat(test(1)).isEqualTo(1);
		}
	}
}
