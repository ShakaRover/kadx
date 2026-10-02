package jadx.tests.integration.arrays;

public class TestArrayFillConstReplaceFixture {

	public static class TestCls {
		public static final int CONST_INT = 0xffff;

		public int[] test() {
			return new int[] { 127, 129, CONST_INT };
		}
	}
}
