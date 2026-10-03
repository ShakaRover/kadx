package jadx.tests.integration.conditions

object TestConditionsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestConditionsFixture {

	public static class TestCls {
		public boolean test(boolean a, boolean b, boolean c) {
			return (a && b) || c;
		}
	}
}
"""
}
