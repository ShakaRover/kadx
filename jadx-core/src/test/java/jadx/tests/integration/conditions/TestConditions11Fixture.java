package jadx.tests.integration.conditions;

public class TestConditions11Fixture {

	public static class TestCls {

		public void test(boolean a, int b) {
			if (a || b > 2) {
				f();
			}
		}

		private void f() {
		}
	}
}
