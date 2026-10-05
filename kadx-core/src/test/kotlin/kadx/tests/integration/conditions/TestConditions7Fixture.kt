package kadx.tests.integration.conditions

object TestConditions7Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestConditions7Fixture {

	public static class TestCls {
		public void test(int[] a, int i) {
			if (i >= 0 && i < a.length) {
				a[i]++;
			}
		}
	}
}
"""
}
