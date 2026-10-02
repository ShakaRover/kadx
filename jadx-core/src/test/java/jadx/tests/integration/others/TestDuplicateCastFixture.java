package jadx.tests.integration.others;

public class TestDuplicateCastFixture {

	public static class TestCls {
		public int[] method(Object o) {
			return (int[]) o;
		}
	}
}
