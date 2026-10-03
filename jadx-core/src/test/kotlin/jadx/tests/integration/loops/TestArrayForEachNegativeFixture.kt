package jadx.tests.integration.loops

object TestArrayForEachNegativeFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

public class TestArrayForEachNegativeFixture {

	public static class TestCls {

		public int test(int[] a, int[] b) {
			int sum = 0;
			for (int i = 0; i < a.length; i += 2) {
				sum += a[i];
			}
			for (int i = 1; i < a.length; i++) {
				sum += a[i];
			}
			for (int i = 0; i < a.length; i--) {
				sum += a[i];
			}
			for (int i = 0; i <= a.length; i++) {
				sum += a[i];
			}
			for (int i = 0; i + 1 < a.length; i++) {
				sum += a[i];
			}
			for (int i = 0; i < a.length; i++) {
				sum += a[i - 1];
			}
			for (int i = 0; i < b.length; i++) {
				sum += a[i];
			}
			int j = 0;
			for (int i = 0; i < a.length; j++) {
				sum += a[j];
			}
			return sum;
		}
	}
}
"""
}
