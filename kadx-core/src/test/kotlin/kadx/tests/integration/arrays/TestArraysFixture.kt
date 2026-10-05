package kadx.tests.integration.arrays

object TestArraysFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.arrays;

public class TestArraysFixture {

	public static class TestCls {

		public int test1(int i) {
			int[] a = new int[] { 1, 2, 3, 5 };
			return a[i];
		}

		public int test2(int i) {
			int[][] a = new int[i][i + 1];
			return a.length;
		}
	}
}
"""
}
