package kadx.tests.integration.invoke

object TestOverloadedInvokeFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

public class TestOverloadedInvokeFixture {

	public static class TestCls {
		public static final int N = 10;

		public void test() {
			int[][][] arr = new int[N][N][N];
			use(arr, -1);
			use(arr[0], -2);
		}

		public void use(Object[][] arr, Object obj) {
		}

		public void use(int[][] arr, int i) {
		}
	}
}
"""
}
