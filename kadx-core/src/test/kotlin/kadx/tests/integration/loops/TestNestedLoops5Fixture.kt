package kadx.tests.integration.loops

object TestNestedLoops5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNestedLoops5Fixture {

	public static class TestCls {

		public int testFor() {
			int tmp = 1;
			for (int i = 5; i > -1; i--) {
				if (i > tmp) {
					for (int j = 1; j < 5; j++) {
						if (tmp > j * 100) {
							return 0;
						}
					}
				}
				tmp++;
			}
			return tmp;
		}

		public void check() {
			assertThat(testFor()).isEqualTo(7);
		}
	}
}
"""
}
