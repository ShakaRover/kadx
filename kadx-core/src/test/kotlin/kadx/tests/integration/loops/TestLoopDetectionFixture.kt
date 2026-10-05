package kadx.tests.integration.loops

object TestLoopDetectionFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLoopDetectionFixture {

	public static class TestCls {

		public void test(int[] a, int b) {
			int i = 0;
			while (i < a.length && i < b) {
				a[i]++;
				i++;
			}
			while (i < a.length) {
				a[i]--;
				i++;
			}
		}

		public void check() {
			int[] a = { 1, 1, 1, 1, 1 };
			test(a, 3);
			assertThat(a).containsExactly(new int[] { 2, 2, 2, 0, 0 });
		}
	}
}
"""
}
