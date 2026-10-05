package kadx.tests.integration.loops

object TestBreakWithLabelFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBreakWithLabelFixture {

	public static class TestCls {

		public boolean test(int[][] arr, int b) {
			boolean found = false;
			loop0: for (int i = 0; i < arr.length; i++) {
				for (int j = 0; j < arr[i].length; j++) {
					if (arr[i][j] == b) {
						found = true;
						break loop0;
					}
				}
			}
			System.out.println("found: " + found);
			return found;
		}

		public void check() {
			int[][] testArray = { { 1, 2 }, { 3, 4 } };
			assertThat(test(testArray, 3)).isTrue();
			assertThat(test(testArray, 5)).isFalse();
		}
	}
}
"""
}
