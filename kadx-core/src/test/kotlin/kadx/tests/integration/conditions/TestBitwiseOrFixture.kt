package kadx.tests.integration.conditions

object TestBitwiseOrFixture {
	class TestCls
	class TestCls2
	class TestCls3
	class TestCls4

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestBitwiseOrFixture {

	public static class TestCls {
		private boolean a;
		private boolean b;

		public void test() {
			if ((a | b) != false) {
				test();
			}
		}
	}

	public static class TestCls2 {
		private boolean a;
		private boolean b;

		public void test() {
			if ((a | b) != true) {
				test();
			}
		}
	}

	public static class TestCls3 {
		private boolean a;
		private boolean b;

		public void test() {
			if ((a | b) == false) {
				test();
			}
		}
	}

	public static class TestCls4 {
		private boolean a;
		private boolean b;

		public void test() {
			if ((a | b) == true) {
				test();
			}
		}
	}
}
"""
}
