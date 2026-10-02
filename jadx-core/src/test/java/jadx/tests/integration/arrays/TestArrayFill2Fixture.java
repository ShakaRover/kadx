package jadx.tests.integration.arrays;

public class TestArrayFill2Fixture {

	public static class TestCls {

		public int[] test(int a) {
			return new int[] { 1, a + 1, 2 };
		}
	}

	public static class TestCls2 {

		public int[] test2(int a) {
			return new int[] { 1, a++, a * 2 };
		}
	}
}
