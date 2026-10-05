package kadx.tests.integration.conditions

object TestConditions2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestConditions2Fixture {

	public static class TestCls {
		int c;
		String d;
		String f;

		public void testComplexIf(String a, int b) {
			if (d == null || (c == 0 && b != -1 && d.length() == 0)) {
				c = a.codePointAt(c);
			} else {
				if (a.hashCode() != 0xCDE) {
					c = f.compareTo(a);
				}
			}
		}
	}
}
"""
}
