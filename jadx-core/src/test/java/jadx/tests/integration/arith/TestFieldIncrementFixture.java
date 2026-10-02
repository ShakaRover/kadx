package jadx.tests.integration.arith;

public class TestFieldIncrementFixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		public int instanceField = 1;
		public static int staticField = 1;
		public static String result = "";

		public void method() {
			instanceField++;
		}

		public void method2() {
			staticField--;
		}

		public void method3(String s) {
			result += s + '_';
		}
	}
}
