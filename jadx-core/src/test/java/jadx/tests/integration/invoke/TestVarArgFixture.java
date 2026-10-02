package jadx.tests.integration.invoke;

public class TestVarArgFixture {

	public static class TestCls {

		public void test1(int... a) {
		}

		public void test2(int i, Object... a) {
		}

		public void test3(int[] a) {
		}

		public void call() {
			test1(1, 2);
			test2(3, "1", 7);
			test3(new int[] { 5, 8 });
		}
	}
}
