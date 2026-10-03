package jadx.tests.integration.code

object TestArrayAccessReorderFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.code;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestArrayAccessReorderFixture {

	public static class TestCls {
		public int[] test(int[] arr) {
			int len = arr.length;
			int[] result = new int[len];
			int i = 0;
			int k = len;
			while (k != 0) {
				int v = arr[i];
				k--;
				int t = -v;
				i++;
				result[k] = t * 5;
			}
			return result;
		}

		public void check() {
			assertThat(test(new int[] { 1, 2, 3 })).isEqualTo(new int[] { -15, -10, -5 });
		}
	}
}
"""
}
