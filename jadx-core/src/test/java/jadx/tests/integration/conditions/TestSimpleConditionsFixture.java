package jadx.tests.integration.conditions;

public class TestSimpleConditionsFixture {

	public static class TestCls {
		public boolean test1(boolean[] a) {
			return (a[0] && a[1] && a[2]) || (a[3] && a[4]);
		}

		public boolean test2(boolean[] a) {
			return a[0] || a[1] || a[2] || a[3];
		}
	}
}
