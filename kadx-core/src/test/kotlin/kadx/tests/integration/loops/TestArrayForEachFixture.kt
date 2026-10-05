package kadx.tests.integration.loops

object TestArrayForEachFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

public class TestArrayForEachFixture {

	public static class TestCls {

		public int test(int[] a) {
			int sum = 0;
			for (int n : a) {
				sum += n;
			}
			return sum;
		}
	}
}
"""
}
