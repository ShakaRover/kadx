package jadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTryCatchInLoopFixture {

	public static class TestCls {
		int c = 0;

		public int test() {
			while (true) {
				try {
					exc();
					break;
				} catch (Exception e) {
					//
				}
			}
			if (c == 5) {
				System.out.println(c);
			}
			return 0;
		}

		private void exc() throws Exception {
			c++;
			if (c < 3) {
				throw new Exception();
			}
		}

		public void check() {
			test();
			assertThat(c).isEqualTo(3);
		}
	}
}
