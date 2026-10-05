package kadx.tests.integration.loops

object TestIndexForLoopFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.assertThat;

public class TestIndexForLoopFixture {

	public static class TestCls {

		private int test(int[] a, int b) {
			int sum = 0;
			for (int i = 0; i < b; i++) {
				sum += a[i];
			}
			return sum;
		}

		public void check() {
			int[] array = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
			assertThat(test(array, 0)).isEqualTo(0);
			assertThat(test(array, 3)).isEqualTo(6);
			assertThat(test(array, 8)).isEqualTo(36);
		}
	}
}
"""
}
