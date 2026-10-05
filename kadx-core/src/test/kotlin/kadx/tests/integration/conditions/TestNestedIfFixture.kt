package kadx.tests.integration.conditions

object TestNestedIfFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestNestedIfFixture {

	public static class TestCls {
		private boolean a0 = false;
		private int a1 = 1;
		private int a2 = 2;
		private int a3 = 1;
		private int a4 = 2;

		public boolean test1() {
			if (a0) {
				if (a1 == 0 || a2 == 0) {
					return false;
				}
			} else if (a3 == 0 || a4 == 0) {
				return false;
			}
			test1();
			return true;
		}
	}
}
"""
}
