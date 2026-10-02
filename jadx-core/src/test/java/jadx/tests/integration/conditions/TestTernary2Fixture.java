package jadx.tests.integration.conditions;

public class TestTernary2Fixture {

	public static class TestCls {

		public void test() {
			checkFalse(f(1, 0) == 0);
		}

		private int f(int a, int b) {
			return a + b;
		}

		private void checkFalse(boolean b) {
			if (b) {
				throw new AssertionError("Must be false");
			}
		}
	}
}
