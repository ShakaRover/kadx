package jadx.tests.integration.conditions

object TestConditions5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestConditions5Fixture {

	public static class TestCls {
		public static void test(Object a1, Object a2) {
			if (a1 == null) {
				if (a2 != null) {
					throw new AssertionError(a1 + " != " + a2);
				}
			} else if (!a1.equals(a2)) {
				throw new AssertionError(a1 + " != " + a2);
			}
		}

		public static void test2(Object a1, Object a2) {
			if (a1 != null) {
				if (!a1.equals(a2)) {
					throw new AssertionError(a1 + " != " + a2);
				}
			} else {
				if (a2 != null) {
					throw new AssertionError(a1 + " != " + a2);
				}
			}
		}
	}
}
"""
}
