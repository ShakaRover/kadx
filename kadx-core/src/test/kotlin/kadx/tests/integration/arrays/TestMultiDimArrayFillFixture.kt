package kadx.tests.integration.arrays

object TestMultiDimArrayFillFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.arrays;

public class TestMultiDimArrayFillFixture {

	public static class TestCls {

		public static Obj test(int a, int b) {
			return new Obj(
					new int[][] { { 1 }, { 2 }, { 3 }, { 4, 5 }, new int[0] },
					new int[] { a, a, a, a, b });
		}

		private static class Obj {
			public Obj(int[][] ints, int[] ints2) {
			}
		}
	}
}
"""
}
