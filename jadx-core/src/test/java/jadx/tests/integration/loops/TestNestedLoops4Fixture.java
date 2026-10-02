package jadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNestedLoops4Fixture {

	public static class TestCls {

		public int testFor() {
			int tmp = 1;
			for (int i = 10; i > -1; i--) {
				if (i > tmp) {
					for (int j = 0; j < 54; j += 4) {
						if (i < j) {
							for (int k = j; k < j + 4; k++) {
								if (tmp > k) {
									return 0;
								}
							}
							break;
						}
					}
				}
				tmp++;
			}
			return tmp;
		}

		public void check() {
			assertThat(testFor()).isEqualTo(12);
		}
	}
}
