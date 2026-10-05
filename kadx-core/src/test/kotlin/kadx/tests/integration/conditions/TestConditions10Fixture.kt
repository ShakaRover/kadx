package kadx.tests.integration.conditions

object TestConditions10Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestConditions10Fixture {

	public static class TestCls {

		public void test(boolean a, int b) {
			if (a || b > 2) {
				b++;
			}
			if (!a || (b >= 0 && b <= 11)) {
				System.out.println("1");
			} else {
				System.out.println("2");
			}
			System.out.println("3");
		}
	}
}
"""
}
