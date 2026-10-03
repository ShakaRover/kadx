package jadx.tests.integration.arrays

object TestArrayFillNegativeFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arrays;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestArrayFillNegativeFixture {

	public static class TestCls {
		public int[] test() {
			int[] arr = new int[3];
			arr[0] = 1;
			arr[1] = arr[0] + 1;
			arr[2] = arr[1] + 1;
			return arr;
		}

		public void check() {
			assertThat(test()).isEqualTo(new int[] { 1, 2, 3 });
		}
	}
}
"""
}
