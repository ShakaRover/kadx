package jadx.tests.integration.conditions

object TestConditions4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestConditions4Fixture {

	public static class TestCls {
		public int test(int num) {
			boolean inRange = (num >= 59 && num <= 66);
			return inRange ? num + 1 : num;
		}
	}
}
"""
}
