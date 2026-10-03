package jadx.tests.integration.conditions

object TestConditions9Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestConditions9Fixture {

	public static class TestCls {
		public void test(boolean a, int b) throws Exception {
			if (!a || (b >= 0 && b <= 11)) {
				System.out.println('1');
			} else {
				System.out.println('2');
			}
		}
	}
}
"""
}
